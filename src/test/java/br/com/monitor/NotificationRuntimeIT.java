package br.com.monitor;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorService;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.monitoring.CheckService;
import br.com.monitor.notification.NotificationRuntime;
import br.com.monitor.notification.NotificationStore;
import br.com.monitor.notification.SmtpDelivery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static br.com.monitor.NotificationFixture.BASE;
import static br.com.monitor.notification.NotificationStore.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationRuntimeIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final GenericContainer<?> smtp = new GenericContainer<>(DockerImageName.parse(SmtpDeliveryIT.IMAGE))
            .withExposedPorts(1025, 8025);
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private NotificationFixture fixture;
    private TestClock clock;

    @BeforeAll
    void start() { db.start(); smtp.start(); }

    @BeforeEach
    void clear() throws Exception {
        open(false);
        jdbc.execute("TRUNCATE monitor.monitors CASCADE");
        api("/api/v1/messages", "DELETE");
    }

    @AfterEach
    void close() { if (context != null) { context.close(); context = null; } }

    @AfterAll
    void stop() { smtp.stop(); db.stop(); }

    @Test
    void contextRestartDeliversPendingDownThenRecoveryAndRetainsHistory() throws Exception {
        var monitor = fixture.monitor();
        var id = fixture.down(monitor);
        fixture.apply(monitor, CheckResult.Outcome.SUCCESS, 60);
        var incident = jdbc.queryForObject("SELECT id FROM monitor.incidents", UUID.class);
        close(); open(true);
        await(() -> count("notification_intents", "status='ACCEPTED'") == 2);
        assertThat(count("notification_attempts", "outcome='ACCEPTED'")).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_intents WHERE id=?", id))
                .containsEntry("attempt_count", 1).containsEntry("status", "ACCEPTED");
        var rows = jdbc.queryForList("SELECT t.type,a.started_at FROM monitor.notification_attempts a"
                + " JOIN monitor.notification_intents i ON i.id=a.notification_id"
                + " JOIN monitor.state_transitions t ON t.id=i.transition_id ORDER BY t.confirmed_at");
        assertThat(rows).extracting(row -> row.get("type")).containsExactly("DOWN", "RECOVERY");
        assertThat(api("/api/v1/messages", "GET").get("total").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT id FROM monitor.incidents", UUID.class)).isEqualTo(incident);
    }

    @Test
    void restartAfterSmtpAcceptanceWithoutLocalConfirmationRecordsUncertaintyAndMayDuplicate() throws Exception {
        var id = fixture.down(fixture.monitor());
        var delivery = context.getBean(NotificationStore.class).claim(BASE).orElseThrow();
        assertThat(context.getBean(SmtpDelivery.class).send(delivery)).isEqualTo(Result.ACCEPTED);
        close(); open(true);
        await(() -> count("notification_attempts", "outcome='UNCERTAIN'") == 1);
        assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_intents WHERE id=?", id))
                .containsEntry("status", "PENDING").containsEntry("attempt_count", 1)
                .containsEntry("next_attempt_at", Timestamp.from(BASE.plusSeconds(30)));
        assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_attempts"))
                .containsEntry("error_code", "RESTART_UNCERTAIN").containsEntry("finished_at", Timestamp.from(BASE));
        assertThat(api("/api/v1/messages", "GET").get("total").asInt()).isEqualTo(1);
        clock.advance(30);
        await(() -> count("notification_intents", "status='ACCEPTED'") == 1);
        assertThat(count("notification_attempts", "TRUE")).isEqualTo(2);
        var messages = api("/api/v1/messages", "GET");
        assertThat(messages.get("total").asInt()).isEqualTo(2);
        for (var message : messages.get("messages")) {
            assertThat(message.get("MessageID").asText())
                    .isEqualTo(delivery.messageId().replace("<", "").replace(">", ""));
        }
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM monitor.notification_intents", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void failedCompletionRetriesPersistenceWithoutSendingAgainInSameProcess() throws Exception {
        var id = fixture.down(fixture.monitor());
        var runtime = new NotificationRuntime(context.getBean(NotificationStore.class),
                context.getBean(SmtpDelivery.class), clock, new MockEnvironment());
        jdbc.execute("ALTER TABLE monitor.notification_attempts ADD CONSTRAINT reject_completion"
                + " CHECK (outcome='SENDING') NOT VALID");
        try {
            assertThatThrownBy(runtime::tick).isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_intents WHERE id=?", id))
                    .containsEntry("status", "SENDING").containsEntry("attempt_count", 1);
            assertThat(api("/api/v1/messages", "GET").get("total").asInt()).isEqualTo(1);
            jdbc.execute("ALTER TABLE monitor.notification_attempts DROP CONSTRAINT reject_completion");
            runtime.tick();
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_intents WHERE id=?", id))
                    .containsEntry("status", "ACCEPTED").containsEntry("attempt_count", 1);
            assertThat(api("/api/v1/messages", "GET").get("total").asInt()).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE monitor.notification_attempts DROP CONSTRAINT IF EXISTS reject_completion");
            runtime.destroy();
        }
    }

    @Test
    void slowSmtpDoesNotHoldDomainTransactionOrPreventHttpChecksAndRecovery() throws Exception {
        var connected = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var socket = new ServerSocket(0); var pool = Executors.newSingleThreadExecutor()) {
            var peer = pool.submit(() -> {
                try (var client = socket.accept()) { connected.countDown(); release.await(4, TimeUnit.SECONDS); }
                catch (Exception error) { throw new IllegalStateException(error); }
            });
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try { exchange.sendResponseHeaders(200, -1); } finally { exchange.close(); }
            });
            server.start();
            try {
                close();
                open(false, "--monitor.smtp.host=127.0.0.1", "--monitor.smtp.port=" + socket.getLocalPort(),
                        "--monitor.smtp.read-timeout-ms=3000", "--monitor.demo-origin=http://127.0.0.1:"
                                + server.getAddress().getPort());
                fixture.down(fixture.monitor());
                var runtime = new NotificationRuntime(context.getBean(NotificationStore.class),
                        context.getBean(SmtpDelivery.class), clock, new MockEnvironment());
                try {
                    runtime.start();
                    assertThat(connected.await(2, TimeUnit.SECONDS)).isTrue();
                    var service = context.getBean(MonitorService.class);
                    var monitor = service.create(MonitorConfiguration.validated("HTTP while SMTP waits",
                            "http://127.0.0.1:" + server.getAddress().getPort(), 60, 5, 200, 1, 1, "ops@example.org"));
                    var result = context.getBean(CheckService.class).executeAndRecord(monitor, UUID.randomUUID());
                    assertThat(result.outcome()).isEqualTo(CheckResult.Outcome.SUCCESS);
                    assertThat(fixture.processor.process(result.executionId())).isTrue();
                    assertThat(service.get(monitor.id()).state().confirmedState()).isEqualTo("UP");
                    assertThat(count("notification_intents", "status='SENDING'")).isEqualTo(1);
                } finally { release.countDown(); runtime.destroy(); }
            } finally { release.countDown(); server.stop(0); }
            peer.get(4, TimeUnit.SECONDS);
        }
    }

    @Test
    void uncertainClaimCommitIsReconciledBeforeAnotherReservation() throws Exception {
        var id = fixture.down(fixture.monitor());
        var lostReply = new java.util.concurrent.atomic.AtomicBoolean(true);
        var store = new NotificationStore(fixture.named, fixture.manager) {
            @Override
            public java.util.Optional<br.com.monitor.notification.NotificationDelivery> claim(Instant now) {
                var delivery = super.claim(now);
                if (lostReply.getAndSet(false)) { throw new IllegalStateException("Lost commit reply"); }
                return delivery;
            }
        };
        var runtime = new NotificationRuntime(store, context.getBean(SmtpDelivery.class), clock, new MockEnvironment());
        try {
            assertThatThrownBy(runtime::tick).hasMessage("Lost commit reply");
            runtime.tick();
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_intents WHERE id=?", id))
                    .containsEntry("status", "PENDING").containsEntry("attempt_count", 1)
                    .containsEntry("next_attempt_at", Timestamp.from(BASE.plusSeconds(30)));
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_attempts"))
                    .containsEntry("outcome", "UNCERTAIN").containsEntry("error_code", "RESTART_UNCERTAIN");
            clock.advance(30);
            runtime.tick();
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_intents WHERE id=?", id))
                    .containsEntry("status", "ACCEPTED").containsEntry("attempt_count", 2);
            assertThat(api("/api/v1/messages", "GET").get("total").asInt()).isEqualTo(1);
        } finally { runtime.destroy(); }
    }

    @Test
    void invalidPollIntervalFailsWithFixedMessage() {
        for (var value : List.of("0", "-1", "60001", "invalid")) {
            assertThatThrownBy(() -> new NotificationRuntime(context.getBean(NotificationStore.class),
                    context.getBean(SmtpDelivery.class), clock,
                    new MockEnvironment().withProperty("monitor.notifications.poll-ms", value)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid notification polling interval");
        }
    }

    private void open(boolean enabled, String... extra) {
        var arguments = new ArrayList<>(List.of("--server.port=0", "--monitor.scheduler.enabled=false",
                "--monitor.notifications.enabled=" + enabled, "--monitor.notifications.poll-ms=20",
                "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(), "--ADMIN_USERNAME=" + UUID.randomUUID(),
                "--ADMIN_PASSWORD=" + UUID.randomUUID(), "--monitor.smtp.host=" + smtp.getHost(),
                "--monitor.smtp.port=" + smtp.getMappedPort(1025)));
        // Explicit test overrides replace defaults instead of passing duplicate command line keys.
        for (String value : extra) {
            String key = value.substring(0, value.indexOf('='));
            arguments.removeIf(existing -> existing.startsWith(key + "=")); arguments.add(value);
        }
        context = new SpringApplicationBuilder(MonitorApplication.class, ClockConfiguration.class)
                .run(arguments.toArray(String[]::new));
        jdbc = context.getBean(JdbcTemplate.class);
        fixture = new NotificationFixture(jdbc, context.getBean(PlatformTransactionManager.class));
        clock = context.getBean(TestClock.class);
    }

    private int count(String table, String predicate) {
        return jdbc.queryForObject("SELECT count(*) FROM monitor." + table + " WHERE " + predicate, Integer.class);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(20); }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private JsonNode api(String path, String method) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://" + smtp.getHost() + ":"
                    + smtp.getMappedPort(8025) + path)).method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return method.equals("DELETE") || response.body().isBlank()
                    ? null : new ObjectMapper().readTree(response.body());
        }
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary
        TestClock notificationClock() { return new TestClock(); }
    }

    static class TestClock extends Clock {
        private volatile Instant now = BASE;
        void advance(int seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
