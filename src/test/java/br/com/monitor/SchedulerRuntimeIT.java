package br.com.monitor;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.administration.MonitorService;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.monitoring.CheckScheduler;
import br.com.monitor.monitoring.CheckService;
import br.com.monitor.monitoring.ScheduleStore;
import br.com.monitor.monitoring.SchedulerRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulerRuntimeIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final String username = UUID.randomUUID().toString();
    private final String password = UUID.randomUUID().toString();
    private final AtomicInteger healthyHits = new AtomicInteger();
    private final AtomicInteger slowHits = new AtomicInteger();
    private HttpServer server;
    private String origin;
    private ServletWebServerApplicationContext context;
    private JdbcTemplate jdbc;
    private MonitorService monitors;
    private MonitorRepository repository;
    private ScheduleStore store;
    private CheckSchedulerTest.MutableClock clock;
    private CountDownLatch slowEntered;
    private CountDownLatch releaseSlow;

    @BeforeAll
    void start() throws Exception {
        db.start();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/slow")) {
                slowHits.incrementAndGet(); slowEntered.countDown();
                try { releaseSlow.await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            } else { healthyHits.incrementAndGet(); }
            try { exchange.sendResponseHeaders(200, -1); }
            catch (java.io.IOException ignored) { }
            finally { exchange.close(); }
        });
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        open(false);
    }

    @BeforeEach
    void clear() {
        if (context.containsBean("schedulerRuntime")) { context.close(); open(false); }
        jdbc.execute("TRUNCATE monitor.check_attempts, monitor.check_results, monitor.monitors CASCADE");
        clock.reset();
        healthyHits.set(0); slowHits.set(0);
        slowEntered = new CountDownLatch(1); releaseSlow = new CountDownLatch(1);
    }

    @AfterAll
    void stop() { releaseSlow.countDown(); context.close(); server.stop(0); db.stop(); }

    @Test
    void slowTimeoutDoesNotPreventHealthyAndExcessMonitorFromAdvancing() throws Exception {
        var slow = create("/slow", 1);
        clock.advance(1);
        var healthy = create("/healthy", 1);
        clock.advance(1);
        var third = create("/third", 1);
        try (var scheduler = scheduler(2)) {
            scheduler.tick();
            assertThat(slowEntered.await(2, TimeUnit.SECONDS)).isTrue();
            await().atMost(Duration.ofSeconds(2)).until(() -> count(healthy.id()) == 1 && scheduler.inFlight() == 1);
            assertThat(count(slow.id())).isZero();
            assertThat(monitors.get(third.id())).isEqualTo(third);
            assertThat(healthyHits.get()).isEqualTo(1);
            scheduler.tick();
            await().atMost(Duration.ofSeconds(2)).until(() -> count(third.id()) == 1);
            assertThat(healthyHits.get()).isEqualTo(2);
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(3))).isTrue();
            var result = jdbc.queryForMap("SELECT * FROM monitor.check_results WHERE monitor_id=?", slow.id());
            assertThat(result.get("outcome")).isEqualTo("TIMEOUT");
            assertThat(((Number) result.get("duration_nanos")).longValue()).isBetween(800_000_000L, 2_500_000_000L);
            assertThat(store.pending()).isEmpty();
            scheduler.tick();
            assertThat(healthyHits.get()).isEqualTo(2);
            assertThat(slowHits.get()).isEqualTo(1);
        } finally { releaseSlow.countDown(); }
    }

    @Test
    void reservationCleanupBeforeWorkerExitStillPreventsAnotherStartOfSameMonitor() throws Exception {
        var monitor = create("/healthy", 1);
        var finished = new CountDownLatch(1);
        var exit = new CountDownLatch(1);
        var held = new ScheduleStore(context.getBean(NamedParameterJdbcTemplate.class), repository,
                context.getBean(CheckRepository.class), context.getBean(PlatformTransactionManager.class)) {
            @Override
            public void finish(Attempt attempt, CheckResult result) {
                super.finish(attempt, result);
                finished.countDown();
                try { exit.await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        };
        try (var scheduler = new CheckScheduler(held,
                context.getBean(CheckService.class)::executeAndRecord, clock, 2)) {
            scheduler.tick();
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(store.pending()).isEmpty();
            clock.advance(60);
            scheduler.tick();
            assertThat(scheduler.inFlight()).isEqualTo(1);
            assertThat(healthyHits.get()).isEqualTo(1);
            assertThat(count(monitor.id())).isEqualTo(1);
            assertThat(monitors.get(monitor.id()).state().nextCheckAt()).isEqualTo(clock.instant());
            exit.countDown();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(healthyHits.get()).isEqualTo(2);
            assertThat(count(monitor.id())).isEqualTo(2);
        } finally { exit.countDown(); }
    }

    @Test
    void pauseDuringRealHttpCancelsWithoutRecoveringIncidentAndResumeRunsOnce() throws Exception {
        var slow = create("/slow", 5);
        var incident = UUID.randomUUID();
        IncidentFixtures.open(jdbc, slow.id(), incident, 1);
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='DOWN', open_incident_id=? WHERE id=?",
                incident, slow.id());
        try (var scheduler = scheduler(1)) {
            scheduler.tick();
            assertThat(slowEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var paused = monitors.pause(slow.id());
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(monitors.get(slow.id())).isEqualTo(paused);
            assertThat(outcome(slow.id())).isEqualTo("CANCELLED");
            assertThat(paused.state().confirmedState()).isEqualTo("DOWN");
            assertThat(paused.state().openIncidentId()).isEqualTo(incident);
            scheduler.tick();
            assertThat(slowHits.get()).isEqualTo(1);
            releaseSlow.countDown();
            var resumed = monitors.resume(slow.id());
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(count(slow.id())).isEqualTo(2);
            assertThat(slowHits.get()).isEqualTo(2);
            assertThat(monitors.get(slow.id()).state().nextCheckAt()).isEqualTo(clock.instant().plusSeconds(60));
            assertThat(monitors.get(slow.id()).state().executionGeneration())
                    .isEqualTo(resumed.state().executionGeneration());
            assertThat(monitors.get(slow.id()).state().confirmedState()).isEqualTo("DOWN");
        } finally { releaseSlow.countDown(); }
    }

    @Test
    void policyEditInvalidatesOldWorkerAndSchedulesOnlyNewVersion() throws Exception {
        var slow = create("/slow", 5);
        try (var scheduler = scheduler(1)) {
            scheduler.tick();
            assertThat(slowEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var edited = monitors.edit(slow.id(), configuration("edited", "/healthy", 1));
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(monitors.get(slow.id())).isEqualTo(edited);
            assertThat(outcome(slow.id())).isEqualTo("CANCELLED");
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            var rows = jdbc.queryForList("SELECT configuration_version, execution_generation, outcome"
                    + " FROM monitor.check_results WHERE monitor_id=? ORDER BY configuration_version", slow.id());
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsEntry("configuration_version", 1L).containsEntry("outcome", "CANCELLED");
            assertThat(rows.get(1)).containsEntry("configuration_version", 2L)
                    .containsEntry("execution_generation", 2L).containsEntry("outcome", "SUCCESS");
            assertThat(monitors.get(slow.id()).state().confirmedState()).isEqualTo("UNKNOWN");
            assertThat(monitors.get(slow.id()).state().currentObservation()).isFalse();
        } finally { releaseSlow.countDown(); }
    }

    @Test
    void nameOnlyEditPreservesRunningAttemptAndRealSuccessHistory() throws Exception {
        var slow = create("/slow", 5);
        try (var scheduler = scheduler(1)) {
            scheduler.tick();
            assertThat(slowEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var renamed = monitors.edit(slow.id(), configuration("renamed", "/slow", 5));
            scheduler.tick();
            assertThat(scheduler.inFlight()).isEqualTo(1);
            releaseSlow.countDown();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(monitors.get(slow.id())).isEqualTo(renamed);
            assertThat(outcome(slow.id())).isEqualTo("SUCCESS");
            assertThat(count(slow.id())).isEqualTo(1);
        } finally { releaseSlow.countDown(); }
    }

    @Test
    void interruptedReservationSurvivesContextRestartThenOverdueRunsWithoutReplay() throws Exception {
        var monitor = create("/healthy", 1);
        var incident = UUID.randomUUID();
        IncidentFixtures.open(jdbc, monitor.id(), incident, 1);
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='DOWN', open_incident_id=?, recovery_count=1,"
                + " recovery_started_at=?, last_observed_at=?, current_observation=true WHERE id=?", incident,
                java.sql.Timestamp.from(clock.instant()), java.sql.Timestamp.from(clock.instant()), monitor.id());
        var attempt = store.claim(clock.instant()).orElseThrow();
        var before = monitors.get(monitor.id());
        context.close(); open(false);
        try (var scheduler = scheduler(1)) {
            scheduler.tick();
            assertThat(count(monitor.id())).isEqualTo(1);
            assertThat(healthyHits.get()).isZero();
            assertThat(context.getBean(CheckRepository.class).find(attempt.executionId()).orElseThrow().outcome())
                    .isEqualTo(Outcome.CANCELLED);
            var recovered = monitors.get(monitor.id());
            assertThat(recovered.configuration()).isEqualTo(before.configuration());
            assertThat(recovered.state().nextCheckAt()).isEqualTo(before.state().nextCheckAt());
            assertThat(recovered.state().confirmedState()).isEqualTo("DOWN");
            assertThat(recovered.state().openIncidentId()).isEqualTo(incident);
            assertThat(recovered.state().recoveryCount()).isZero();
            clock.advance(600);
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            scheduler.tick();
            assertThat(count(monitor.id())).isEqualTo(2);
            assertThat(healthyHits.get()).isEqualTo(1);
            assertThat(monitors.get(monitor.id()).state().nextCheckAt()).isEqualTo(clock.instant().plusSeconds(60));
        }
    }

    @Test
    void restartAfterResultBeforeReservationCleanupPreservesFirstResult() throws Exception {
        var monitor = create("/healthy", 1);
        var attempt = store.claim(clock.instant()).orElseThrow();
        var saved = context.getBean(CheckService.class).executeAndRecord(monitor, attempt.executionId());
        jdbc.update("UPDATE monitor.monitors SET failure_count=2 WHERE id=?", monitor.id());
        var before = monitors.get(monitor.id());
        context.close(); open(false);
        try (var scheduler = scheduler(1)) {
            scheduler.tick();
            assertThat(scheduler.inFlight()).isZero();
            assertThat(store.pending()).isEmpty();
            assertThat(monitors.get(monitor.id())).isEqualTo(before);
            assertThat(context.getBean(CheckRepository.class).find(saved.executionId())).contains(saved);
            assertThat(count(monitor.id())).isEqualTo(1);
            assertThat(healthyHits.get()).isEqualTo(1);
        }
    }

    @Test
    void realPersistenceFailureLeavesRecoverableReservationWithoutExternalFabrication() throws Exception {
        var monitor = create("/healthy", 1);
        jdbc.execute("ALTER TABLE monitor.check_results ADD CONSTRAINT reject_result CHECK (false)");
        try (var scheduler = scheduler(1)) {
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(count(monitor.id())).isZero();
            assertThat(store.pending()).containsExactly(monitor.id());
            jdbc.execute("ALTER TABLE monitor.check_results DROP CONSTRAINT reject_result");
            scheduler.tick();
            assertThat(scheduler.inFlight()).isZero();
            assertThat(store.pending()).isEmpty();
            assertThat(outcome(monitor.id())).isEqualTo("CANCELLED");
            assertThat(healthyHits.get()).isEqualTo(1);
            assertThat(monitors.get(monitor.id()).state().confirmedState()).isEqualTo("UNKNOWN");
        } finally { jdbc.execute("ALTER TABLE monitor.check_results DROP CONSTRAINT IF EXISTS reject_result"); }
    }

    @Test
    void apiRepresentsExpiredObservationWithoutInventingCheckResult() throws Exception {
        var monitor = create("/healthy", 5);
        jdbc.update("UPDATE monitor.monitors SET last_observed_at=?, current_observation=true, confirmed_state='UP',"
                + " next_check_at=? WHERE id=?", java.sql.Timestamp.from(clock.instant()),
                java.sql.Timestamp.from(clock.instant().plusSeconds(3600)), monitor.id());
        clock.advance(125);
        var fresh = view(monitor.id());
        assertThat(fresh.path("evidence").asText()).isEqualTo("FRESH");
        assertThat(fresh.path("recentObservation").asBoolean()).isTrue();
        clock.advance(1);
        try (var scheduler = scheduler(1)) { scheduler.tick(); }
        var stale = view(monitor.id());
        assertThat(stale.path("evidence").asText()).isEqualTo("STALE");
        assertThat(stale.path("recentObservation").asBoolean()).isFalse();
        assertThat(stale.path("confirmedState").asText()).isEqualTo("UP");
        assertThat(count(monitor.id())).isZero();
        assertThat(healthyHits.get()).isZero();
    }

    @Test
    void enabledRuntimeStartsAutomaticallyAndHonorsPersistedPauseFutureAndCapacity() {
        var first = create("/healthy", 1);
        var paused = monitors.pause(create("/paused", 1).id());
        var future = create("/future", 1);
        repository.schedule(future.id(), clock.instant().plusSeconds(60));
        context.close(); open(true);
        await().atMost(Duration.ofSeconds(3)).until(() -> count(first.id()) == 1);
        assertThat(healthyHits.get()).isEqualTo(1);
        assertThat(count(paused.id())).isZero();
        assertThat(count(future.id())).isZero();
        assertThat(monitors.get(paused.id())).isEqualTo(paused);
        assertThat(monitors.get(first.id()).state().nextCheckAt()).isEqualTo(clock.instant().plusSeconds(60));
        clock.advance(60);
        await().atMost(Duration.ofSeconds(3)).until(() -> count(first.id()) == 2 && count(future.id()) == 1);
        assertThat(healthyHits.get()).isEqualTo(3);
        assertThat(context.getBean(SchedulerRuntime.class).scheduler().inFlight()).isLessThanOrEqualTo(1);
    }

    @Test
    void runtimeShutdownCancelsHttpBeforeClosingDatabase() throws Exception {
        create("/slow", 5);
        context.close(); open(true);
        assertThat(slowEntered.await(2, TimeUnit.SECONDS)).isTrue();
        context.close(); open(false);
        assertThat(jdbc.queryForObject("SELECT outcome FROM monitor.check_results", String.class))
                .isEqualTo("CANCELLED");
        assertThat(store.pending()).isEmpty();
        assertThat(slowHits.get()).isEqualTo(1);
        releaseSlow.countDown();
    }

    @ParameterizedTest
    @ValueSource(strings = {"concurrency=0", "concurrency=17", "poll-ms=0", "poll-ms=-1"})
    void invalidRuntimeConfigurationPreventsStartup(String property) {
        assertThatThrownBy(() -> {
            try (var invalid = application().run(arguments(true, "--monitor.scheduler." + property))) { }
        }).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void v3UpgradePreservesConfigurationResultAndMigrationHistory() {
        try (var previous = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            previous.start();
            var source = new DriverManagerDataSource(previous.getJdbcUrl(),
                    previous.getUsername(), previous.getPassword());
            Flyway.configure().dataSource(source).target("3").load().migrate();
            var sql = new JdbcTemplate(source);
            var named = new NamedParameterJdbcTemplate(source);
            var repo = new MonitorRepository(named);
            var monitor = Monitor.create(UUID.randomUUID(), configuration("upgrade", "/healthy", 1), clock.instant());
            repo.insert(monitor);
            var result = new CheckResult(monitor.id(), UUID.randomUUID(), 1, 1, clock.instant(), clock.instant(),
                    42, Outcome.SUCCESS, 200);
            new CheckRepository(named).save(result);
            var history = sql.queryForList("SELECT * FROM public.flyway_schema_history ORDER BY installed_rank");
            var flyway = Flyway.configure().dataSource(source).target("4").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(sql.queryForList("SELECT * FROM public.flyway_schema_history WHERE version IN ('1','2','3')"
                    + " ORDER BY installed_rank")).isEqualTo(history);
            assertThat(repo.find(monitor.id(), false)).contains(monitor);
            assertThat(new CheckRepository(named).find(result.executionId())).contains(result);
            assertThat(sql.queryForObject("SELECT count(*) FROM monitor.check_attempts", Integer.class)).isZero();
        }
    }

    private CheckScheduler scheduler(int capacity) {
        return new CheckScheduler(store, context.getBean(CheckService.class)::executeAndRecord, clock, capacity);
    }

    private Monitor create(String path, int timeout) { return monitors.create(configuration(path, path, timeout)); }

    private MonitorConfiguration configuration(String name, String path, int timeout) {
        return MonitorConfiguration.validated(name, origin + path, 60, timeout, 200, 3, 2, "ops@e.org");
    }

    private int count(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM monitor.check_results WHERE monitor_id=?", Integer.class, id);
    }

    private String outcome(UUID id) {
        return jdbc.queryForObject("SELECT outcome FROM monitor.check_results WHERE monitor_id=?", String.class, id);
    }

    private com.fasterxml.jackson.databind.JsonNode view(UUID id) throws Exception {
        String basic = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + context.getWebServer().getPort()
                + "/api/admin/monitors/" + id)).header("Authorization", "Basic " + basic).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return new ObjectMapper().readTree(response.body());
    }

    private void open(boolean enabled) {
        context = (ServletWebServerApplicationContext) application().run(arguments(enabled, "--test.flag=true"));
        jdbc = context.getBean(JdbcTemplate.class);
        monitors = context.getBean(MonitorService.class);
        repository = context.getBean(MonitorRepository.class);
        store = context.getBean(ScheduleStore.class);
        clock = context.getBean(CheckSchedulerTest.MutableClock.class);
    }

    private SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(MonitorApplication.class, ControlledClock.class);
    }

    private String[] arguments(boolean enabled, String extra) {
        var arguments = new java.util.ArrayList<>(java.util.List.of("--server.port=0",
            "--spring.datasource.url=" + db.getJdbcUrl(),
            "--spring.datasource.username=" + db.getUsername(), "--spring.datasource.password=" + db.getPassword(),
            "--ADMIN_USERNAME=" + username, "--ADMIN_PASSWORD=" + password, "--monitor.demo-origin=" + origin,
            "--monitor.scheduler.enabled=" + enabled, "--monitor.scheduler.concurrency=1",
            "--monitor.scheduler.poll-ms=10"));
        arguments.removeIf(value -> value.startsWith(extra.split("=", 2)[0] + "="));
        arguments.add(extra);
        return arguments.toArray(String[]::new);
    }

    @TestConfiguration
    static class ControlledClock {
        @Bean @Primary
        CheckSchedulerTest.MutableClock controlledClock() { return new CheckSchedulerTest.MutableClock(); }
    }
}
