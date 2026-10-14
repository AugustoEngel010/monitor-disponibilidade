package br.com.monitor;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorService;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.monitoring.CheckService;
import br.com.monitor.monitoring.DestinationPolicy;
import br.com.monitor.monitoring.HttpCheckExecutor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckServiceIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger status = new AtomicInteger(200);
    private HttpServer server;
    private ConfigurableApplicationContext context;
    private String origin;
    private JdbcTemplate jdbc;
    private MonitorService monitors;
    private CheckService checks;
    private CheckRepository repository;

    @BeforeAll
    void start() throws Exception {
        db.start();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            byte[] body = "response-body-secret".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
            catch (java.io.IOException ignored) { }
            finally { exchange.close(); }
        });
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        context = open(origin);
        jdbc = context.getBean(JdbcTemplate.class);
        monitors = context.getBean(MonitorService.class);
        checks = context.getBean(CheckService.class);
        repository = context.getBean(CheckRepository.class);
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE monitor.check_attempts, monitor.check_results, monitor.monitors");
        hits.set(0);
        status.set(200);
    }

    @AfterAll
    void stop() { context.close(); server.stop(0); db.stop(); }

    @Test
    void executesAndRecordsHeadersThenPreservesFirstResultOnRepeatedId() {
        var monitor = create(origin + "/health");
        var id = UUID.randomUUID();
        var result = checks.executeAndRecord(monitor, id);
        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.monitorId()).isEqualTo(monitor.id());
        assertThat(result.executionId()).isEqualTo(id);
        assertThat(result.configurationVersion()).isEqualTo(1);
        assertThat(result.executionGeneration()).isEqualTo(1);
        assertThat(result.startedAt()).isBeforeOrEqualTo(result.finishedAt());
        assertThat(result.durationNanos()).isPositive();
        assertThat(repository.find(id)).contains(result);
        assertThat(monitors.get(monitor.id())).isEqualTo(monitor);
        status.set(503);
        assertThat(checks.executeAndRecord(monitor, id)).isEqualTo(result);
        assertThat(hits.get()).isEqualTo(2);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT error_message FROM monitor.check_results", String.class)).isNull();
        assertThat(jdbc.queryForMap("SELECT * FROM monitor.check_results").toString())
                .doesNotContain("response-body-secret", origin);
    }

    @Test
    void noDemoConfigurationBlocksLocalDestinationWithoutAccessingIt() {
        var monitor = create(origin);
        try (var restricted = open("")) {
            var result = restricted.getBean(CheckService.class).executeAndRecord(monitor, UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.DESTINATION_BLOCKED);
            assertThat(result.outcome().external()).isFalse();
            assertThat(result.httpStatus()).isNull();
            assertThat(repository.find(result.executionId())).contains(result);
            assertThat(hits.get()).isZero();
            assertThat(count()).isEqualTo(1);
        }
    }

    @Test
    void explicitDemoOriginDoesNotPermitAliasForSamePrivateIp() {
        var monitor = create(origin.replace("127.0.0.1", "localhost"));
        var result = checks.executeAndRecord(monitor, UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(Outcome.DESTINATION_BLOCKED);
        assertThat(result.errorMessage()).isEqualTo("Destination blocked by policy");
        assertThat(hits.get()).isZero();
        assertThat(repository.find(result.executionId())).contains(result);
    }

    @Test
    void oldSnapshotIsRecordedWithoutChangingPausedMonitor() {
        var original = create(origin);
        var paused = monitors.pause(original.id());
        var result = checks.executeAndRecord(original, UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(result.configurationVersion()).isEqualTo(original.state().configurationVersion());
        assertThat(result.executionGeneration()).isEqualTo(1);
        assertThat(monitors.get(original.id())).isEqualTo(paused);
        assertThat(paused.state().executionGeneration()).isEqualTo(2);
        assertThat(repository.find(result.executionId())).contains(result);
    }

    @Test
    void cancellationIsPersistedAndInterruptFlagIsRestored() throws Exception {
        try (var peer = new HttpCheckExecutorIT.Peer(200, false)) {
            var monitor = create(peer.url());
            var service = new CheckService(new HttpCheckExecutor(new DestinationPolicy(
                    HttpCheckExecutorIT.origin(peer.url()))), repository);
            var id = UUID.randomUUID();
            var result = new AtomicReference<CheckResult>();
            var interrupted = new AtomicReference<Boolean>();
            Thread caller = Thread.ofVirtual().start(() -> {
                result.set(service.executeAndRecord(monitor, id));
                interrupted.set(Thread.currentThread().isInterrupted());
            });
            assertThat(peer.accepted.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(2000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(result.get().outcome()).isEqualTo(Outcome.CANCELLED);
            assertThat(interrupted.get()).isTrue();
            assertThat(repository.find(id)).contains(result.get());
            assertThat(result.get().httpStatus()).isNull();
            assertThat(count()).isEqualTo(1);
            assertThat(peer.closed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void persistenceFailurePropagatesAndRestoresInterruptFlagWithoutPartialResult() {
        var monitor = create(origin);
        var id = UUID.randomUUID();
        jdbc.execute("ALTER TABLE monitor.check_results RENAME TO unavailable_results");
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> checks.executeAndRecord(monitor, id)).isInstanceOf(DataAccessException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            jdbc.execute("ALTER TABLE monitor.unavailable_results RENAME TO check_results");
        }
        assertThat(repository.find(id)).isEmpty();
        assertThat(hits.get()).isZero();
        assertThat(count()).isZero();
        assertThat(checks.executeAndRecord(monitor, id).outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(count()).isEqualTo(1);
    }

    private Monitor create(String url) {
        return monitors.create(MonitorConfiguration.validated("integration", url, 10, 1, 200, 3, 2,
                "ops@example.org"));
    }

    private long count() { return jdbc.queryForObject("SELECT count(*) FROM monitor.check_results", Long.class); }

    private ConfigurableApplicationContext open(String allowedOrigin) {
        return new SpringApplicationBuilder(MonitorApplication.class).run("--server.port=0",
                "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(), "--ADMIN_USERNAME=" + UUID.randomUUID(),
                "--ADMIN_PASSWORD=" + UUID.randomUUID(), "--monitor.demo-origin=" + allowedOrigin);
    }
}
