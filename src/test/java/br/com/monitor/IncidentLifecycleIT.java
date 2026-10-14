package br.com.monitor;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorProblem;
import br.com.monitor.administration.MonitorService;
import br.com.monitor.incident.IncidentProcessor;
import br.com.monitor.incident.IncidentRepository;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.monitoring.CheckScheduler;
import br.com.monitor.monitoring.CheckService;
import br.com.monitor.monitoring.ScheduleStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncidentLifecycleIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final AtomicInteger statusCode = new AtomicInteger(503);
    private final AtomicInteger hits = new AtomicInteger();
    private HttpServer server;
    private String origin;
    private ConfigurableApplicationContext context;
    private CheckSchedulerTest.MutableClock clock;
    private JdbcTemplate jdbc;
    private MonitorService monitors;
    private ScheduleStore store;
    private CheckRepository checks;
    private IncidentRepository incidents;
    private IncidentProcessor processor;
    private Monitor monitor;
    private Instant base;

    @BeforeAll
    void start() throws Exception {
        db.start();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            try { exchange.sendResponseHeaders(statusCode.get(), -1); }
            finally { exchange.close(); }
        });
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
        open();
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE monitor.monitors CASCADE");
        clock.reset();
        base = clock.instant();
        monitor = monitors.create(config("lifecycle", "ops@example.org", 3, 2));
        statusCode.set(503);
        hits.set(0);
    }

    @AfterAll
    void stop() { context.close(); server.stop(0); db.stop(); }

    @Test
    void realScheduledHttpConfirmsDownAndRecoveryAndReusesNoClosedIncident() throws Exception {
        // INC-01/03/04/08: production executor, scheduler, Spring beans and PostgreSQL.
        try (var scheduler = new CheckScheduler(store, context.getBean(CheckService.class)::executeAndRecord,
                clock, 1)) {
            for (int i = 0; i < 3; i++) {
                scheduler.tick();
                assertThat(scheduler.awaitIdle(Duration.ofSeconds(3))).isTrue();
                if (i < 2) { assertThat(current().state().openIncidentId()).isNull(); clock.advance(60); }
            }
            var id = current().state().openIncidentId();
            assertThat(current().state().confirmedState()).isEqualTo("DOWN");
            assertThat(incidents.find(id).orElseThrow().firstFailureAt()).isEqualTo(base);
            assertThat(incidents.find(id).orElseThrow().downConfirmedAt()).isEqualTo(base.plusSeconds(120));
            clock.advance(60); statusCode.set(200);
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(3))).isTrue();
            assertThat(current().state().recoveryCount()).isEqualTo(1);
            assertThat(incidents.find(id).orElseThrow().recoveredAt()).isNull();
            clock.advance(60);
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(3))).isTrue();
            assertThat(current().state().confirmedState()).isEqualTo("UP");
            var closed = incidents.find(id).orElseThrow();
            assertThat(closed.recoveryStartedAt()).isEqualTo(base.plusSeconds(180));
            assertThat(closed.recoveredAt()).isEqualTo(base.plusSeconds(240));
            statusCode.set(503);
            for (int i = 0; i < 3; i++) {
                clock.advance(60); scheduler.tick();
                assertThat(scheduler.awaitIdle(Duration.ofSeconds(3))).isTrue();
            }
            assertThat(current().state().openIncidentId()).isNotEqualTo(id);
            assertThat(incidents.find(id)).contains(closed);
            assertThat(count("incidents")).isEqualTo(2);
            assertThat(count("state_transitions")).isEqualTo(3);
            assertThat(count("notification_intents")).isEqualTo(3);
            assertThat(hits.get()).isEqualTo(8);
            assertThat(store.pending()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {60, 126})
    void persistedFailureCountersSurviveRestartOnlyWithinFreshness(int gap) {
        finish(Outcome.HTTP_FAILURE);
        clock.advance(60); finish(Outcome.HTTP_FAILURE);
        var before = current();
        restart();
        assertThat(current()).isEqualTo(before);
        clock.advance(gap); finish(Outcome.HTTP_FAILURE);
        assertThat(current().state().failureCount()).isEqualTo(gap == 60 ? 3 : 1);
        assertThat(current().state().confirmedState()).isEqualTo(gap == 60 ? "DOWN" : "UNKNOWN");
        assertThat(count("incidents")).isEqualTo(gap == 60 ? 1 : 0);
        assertThat(current().state().firstFailureAt()).isEqualTo(gap == 60 ? base : base.plusSeconds(186));
    }

    @Test
    void recoveryCounterAndIncidentSurviveContextRestartWithoutInterruptedAttempt() {
        down();
        clock.advance(60); finish(Outcome.SUCCESS);
        var before = current();
        var incident = incidents.find(before.state().openIncidentId()).orElseThrow();
        restart();
        assertThat(current()).isEqualTo(before);
        assertThat(incidents.find(incident.id())).contains(incident);
        clock.advance(60); finish(Outcome.SUCCESS);
        assertThat(current().state().confirmedState()).isEqualTo("UP");
        assertThat(incidents.find(incident.id()).orElseThrow().recoveryStartedAt()).isEqualTo(base.plusSeconds(180));
        assertThat(incidents.find(incident.id()).orElseThrow().recoveredAt()).isEqualTo(base.plusSeconds(240));
    }

    @Test
    void pauseResumeAreIdempotentPreserveHistoryAndRequireNewRecoverySequence() {
        down();
        clock.advance(60); finish(Outcome.SUCCESS);
        var id = current().state().openIncidentId();
        var intents = jdbc.queryForList("SELECT * FROM monitor.notification_intents ORDER BY id");
        var paused = monitors.pause(monitor.id());
        assertThat(monitors.pause(monitor.id())).isEqualTo(paused);
        assertThat(paused.state().confirmedState()).isEqualTo("DOWN");
        assertThat(paused.state().openIncidentId()).isEqualTo(id);
        assertThat(paused.state().recoveryCount()).isZero();
        assertThat(paused.state().currentObservation()).isFalse();
        assertThat(incidents.find(id).orElseThrow().recoveryStartedAt()).isNull();
        assertThat(store.claim(clock.instant())).isEmpty();
        var resumed = monitors.resume(monitor.id());
        assertThat(monitors.resume(monitor.id())).isEqualTo(resumed);
        assertThat(resumed.state().confirmedState()).isEqualTo("DOWN");
        assertThat(resumed.state().openIncidentId()).isEqualTo(id);
        assertThat(resumed.evidence(clock.instant())).isEqualTo("NEVER_OBSERVED");
        restart();
        assertThat(current()).isEqualTo(resumed);
        finish(Outcome.SUCCESS);
        assertThat(current().state().recoveryCount()).isEqualTo(1);
        assertThat(incidents.find(id).orElseThrow().recoveredAt()).isNull();
        assertThat(jdbc.queryForList("SELECT * FROM monitor.notification_intents ORDER BY id")).isEqualTo(intents);
        clock.advance(60); finish(Outcome.SUCCESS);
        assertThat(incidents.find(id).orElseThrow().recoveryStartedAt()).isEqualTo(base.plusSeconds(180));
        assertThat(incidents.find(id).orElseThrow().recoveredAt()).isEqualTo(base.plusSeconds(240));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAUSE", "RESUME", "POLICY"})
    void invalidatedLateResultIsHistoryOnlyAndReplayCannotApplyIt(String action) {
        var attempt = store.claim(clock.instant()).orElseThrow();
        var late = result(attempt, Outcome.SUCCESS);
        monitors.pause(monitor.id());
        if (action.equals("RESUME")) { monitors.resume(monitor.id()); }
        if (action.equals("POLICY")) {
            monitors.edit(monitor.id(), MonitorConfiguration.validated("new", origin + "/other",
                    60, 5, 200, 3, 2, "ops@example.org"));
            monitors.resume(monitor.id());
        }
        var before = current();
        store.finish(attempt, late);
        assertThat(current()).isEqualTo(before);
        assertThat(checks.find(late.executionId())).contains(late);
        assertThat(processor.process(late.executionId())).isFalse();
        assertThat(current()).isEqualTo(before);
        assertThat(count("incidents")).isZero();
        assertThat(count("state_transitions")).isZero();
        assertThat(store.pending()).isEmpty();
    }

    @Test
    void administrativeEditsPreserveRecoveryAndFrozenIntentAndRejectEveryPolicyField() {
        down();
        clock.advance(60); finish(Outcome.SUCCESS);
        var before = current();
        var intentions = jdbc.queryForList("SELECT * FROM monitor.notification_intents ORDER BY id");
        var renamed = monitors.edit(monitor.id(), config("renamed", "new@example.org", 3, 2));
        assertThat(renamed.state()).isEqualTo(before.state());
        assertThat(jdbc.queryForList("SELECT * FROM monitor.notification_intents ORDER BY id")).isEqualTo(intentions);
        for (var changed : List.of(
                MonitorConfiguration.validated("renamed", origin + "/other", 60, 5, 200, 3, 2, "new@example.org"),
                MonitorConfiguration.validated("renamed", origin, 61, 5, 200, 3, 2, "new@example.org"),
                MonitorConfiguration.validated("renamed", origin, 60, 6, 200, 3, 2, "new@example.org"),
                MonitorConfiguration.validated("renamed", origin, 60, 5, 201, 3, 2, "new@example.org"),
                config("renamed", "new@example.org", 4, 2), config("renamed", "new@example.org", 3, 3))) {
            assertThatThrownBy(() -> monitors.edit(monitor.id(), changed))
                    .isInstanceOfSatisfying(MonitorProblem.class, error -> assertThat(error.status()).isEqualTo(409));
            assertThat(current()).isEqualTo(renamed);
        }
        clock.advance(60); finish(Outcome.SUCCESS);
        assertThat(current().state().confirmedState()).isEqualTo("UP");
        assertThat(jdbc.queryForObject("SELECT recipient FROM monitor.notification_intents i"
                + " JOIN monitor.state_transitions t ON t.id=i.transition_id WHERE t.type='RECOVERY'", String.class))
                .isEqualTo("new@example.org");
        assertThat(jdbc.queryForObject("SELECT recipient FROM monitor.notification_intents i"
                + " JOIN monitor.state_transitions t ON t.id=i.transition_id WHERE t.type='DOWN'", String.class))
                .isEqualTo("ops@example.org");
    }

    @Test
    void restartCancelsInterruptedAttemptAndClearsBothPersistedRecoveryTimes() {
        down();
        clock.advance(60); finish(Outcome.SUCCESS);
        var id = current().state().openIncidentId();
        clock.advance(60);
        var attempt = store.claim(clock.instant()).orElseThrow();
        var before = current();
        restart();
        store.recover(monitor.id(), clock.instant());
        var cancelled = checks.find(attempt.executionId()).orElseThrow();
        assertThat(cancelled.outcome()).isEqualTo(Outcome.CANCELLED);
        assertThat(current().configuration()).isEqualTo(before.configuration());
        assertThat(current().state().confirmedState()).isEqualTo("DOWN");
        assertThat(current().state().openIncidentId()).isEqualTo(id);
        assertThat(current().state().recoveryCount()).isZero();
        assertThat(current().state().recoveryStartedAt()).isNull();
        assertThat(incidents.find(id).orElseThrow().recoveryStartedAt()).isNull();
        assertThat(current().state().lastObservedAt()).isEqualTo(before.state().lastObservedAt());
        assertThat(current().state().nextCheckAt()).isEqualTo(before.state().nextCheckAt());
        var after = current();
        store.recover(monitor.id(), clock.instant().plusSeconds(100));
        assertThat(current()).isEqualTo(after);
        assertThat(checks.find(cancelled.executionId())).contains(cancelled);
        assertThat(store.pending()).isEmpty();
        clock.advance(60); finish(Outcome.SUCCESS);
        assertThat(current().state().recoveryCount()).isEqualTo(1);
        assertThat(incidents.find(id).orElseThrow().recoveredAt()).isNull();
    }

    @Test
    void committedResultBeforeApplicationSurvivesRestartAndIsAppliedBeforeReservationRelease() {
        finish(Outcome.HTTP_FAILURE);
        clock.advance(60); finish(Outcome.HTTP_FAILURE);
        clock.advance(60);
        var attempt = store.claim(clock.instant()).orElseThrow();
        var third = checks.save(result(attempt, Outcome.HTTP_FAILURE));
        restart();
        store.recover(monitor.id(), clock.instant().plusSeconds(999));
        assertThat(checks.find(third.executionId())).contains(third);
        assertThat(current().state().confirmedState()).isEqualTo("DOWN");
        assertThat(incidents.find(current().state().openIncidentId()).orElseThrow().downConfirmedAt())
                .isEqualTo(base.plusSeconds(120));
        var before = current();
        store.recover(monitor.id(), clock.instant().plusSeconds(1000));
        assertThat(current()).isEqualTo(before);
        assertThat(count("state_transitions")).isEqualTo(1);
        assertThat(count("notification_intents")).isEqualTo(1);
        assertThat(store.pending()).isEmpty();
    }

    @Test
    void failedApplicationKeepsReservationAndRawResultUntilRecoveryWithoutAnotherHttp() throws Exception {
        finish(Outcome.HTTP_FAILURE);
        clock.advance(60); finish(Outcome.HTTP_FAILURE);
        clock.advance(60);
        jdbc.execute("ALTER TABLE monitor.notification_intents ADD CONSTRAINT reject_intent CHECK (false)");
        try (var scheduler = new CheckScheduler(store, context.getBean(CheckService.class)::executeAndRecord,
                clock, 1)) {
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(3))).isTrue();
            assertThat(store.pending()).containsExactly(monitor.id());
            assertThat(current().state().failureCount()).isEqualTo(2);
            assertThat(count("incidents")).isZero();
            assertThat(count("state_transitions")).isZero();
            assertThat(count("check_results")).isEqualTo(3);
            jdbc.execute("ALTER TABLE monitor.notification_intents DROP CONSTRAINT reject_intent");
            restart();
            store.recover(monitor.id(), clock.instant().plusSeconds(999));
            assertThat(current().state().confirmedState()).isEqualTo("DOWN");
            assertThat(count("check_results")).isEqualTo(3);
            assertThat(hits.get()).isEqualTo(1);
            assertThat(count("incidents")).isEqualTo(1);
            assertThat(count("notification_intents")).isEqualTo(1);
            assertThat(store.pending()).isEmpty();
        } finally { jdbc.execute("ALTER TABLE monitor.notification_intents DROP CONSTRAINT IF EXISTS reject_intent"); }
    }

    @Test
    void pauseAndThirdFailureSerializeWithNoPartialIncident() throws Exception {
        finish(Outcome.HTTP_FAILURE);
        clock.advance(60); finish(Outcome.HTTP_FAILURE);
        clock.advance(60);
        var attempt = store.claim(clock.instant()).orElseThrow();
        var third = checks.save(result(attempt, Outcome.HTTP_FAILURE));
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = pool.invokeAll(List.<Callable<Boolean>>of(
                    () -> { barrier.await(); return processor.process(third.executionId()); },
                    () -> { barrier.await(); monitors.pause(monitor.id()); return true; }));
            boolean applied = calls.get(0).get();
            assertThat(calls.get(1).get()).isTrue();
            assertThat(current().state().lifecycle()).isEqualTo("PAUSED");
            assertThat(current().state().confirmedState()).isEqualTo(applied ? "DOWN" : "UNKNOWN");
            assertThat(current().state().failureCount()).isZero();
            assertThat(count("incidents")).isEqualTo(applied ? 1 : 0);
            assertThat(count("state_transitions")).isEqualTo(applied ? 1 : 0);
            assertThat(count("notification_intents")).isEqualTo(applied ? 1 : 0);
            if (applied) { assertThat(current().state().openIncidentId()).isNotNull(); }
            else { assertThat(current().state().openIncidentId()).isNull(); }
        }
        var paused = current();
        store.finish(attempt, third);
        assertThat(current()).isEqualTo(paused);
        assertThat(store.pending()).isEmpty();
    }

    @Test
    void lifecycleFailureRollsBackPauseAndIncidentRecoveryTogether() {
        down();
        clock.advance(60); finish(Outcome.SUCCESS);
        var before = current();
        var id = before.state().openIncidentId();
        var incident = incidents.find(id).orElseThrow();
        jdbc.execute("ALTER TABLE monitor.incidents ADD CONSTRAINT reject_lifecycle CHECK (false) NOT VALID");
        try { assertThatThrownBy(() -> monitors.pause(monitor.id())).isInstanceOf(DataAccessException.class); }
        finally { jdbc.execute("ALTER TABLE monitor.incidents DROP CONSTRAINT reject_lifecycle"); }
        assertThat(current()).isEqualTo(before);
        assertThat(incidents.find(id)).contains(incident);
    }

    @Test
    void mismatchedResultCannotApplyOrReleaseAnotherReservation() {
        var attempt = store.claim(clock.instant()).orElseThrow();
        var before = current();
        var wrong = new CheckResult(monitor.id(), UUID.randomUUID(), 1, 1, clock.instant(), clock.instant(),
                0, Outcome.HTTP_FAILURE, 503);
        assertThatThrownBy(() -> store.finish(attempt, wrong)).isInstanceOf(IllegalArgumentException.class);
        assertThat(current()).isEqualTo(before);
        assertThat(store.pending()).containsExactly(monitor.id());
        assertThat(checks.find(wrong.executionId())).isEmpty();
        assertThat(count("result_applications")).isZero();
        assertThat(count("state_transitions")).isZero();
    }

    private void down() {
        finish(Outcome.HTTP_FAILURE);
        clock.advance(60); finish(Outcome.HTTP_FAILURE);
        clock.advance(60); finish(Outcome.HTTP_FAILURE);
    }

    private void finish(Outcome outcome) {
        var attempt = store.claim(clock.instant()).orElseThrow();
        store.finish(attempt, result(attempt, outcome));
    }

    private CheckResult result(ScheduleStore.Attempt attempt, Outcome outcome) {
        return new CheckResult(monitor.id(), attempt.executionId(), attempt.monitor().state().configurationVersion(),
                attempt.monitor().state().executionGeneration(), attempt.startedAt(), clock.instant(), 0, outcome,
                outcome == Outcome.SUCCESS ? Integer.valueOf(200)
                        : outcome == Outcome.HTTP_FAILURE ? Integer.valueOf(503) : null);
    }

    private Monitor current() { return monitors.get(monitor.id()); }
    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM monitor." + table, Integer.class);
    }

    private MonitorConfiguration config(String name, String recipient, int failures, int successes) {
        return MonitorConfiguration.validated(name, origin, 60, 5, 200, failures, successes, recipient);
    }

    private void restart() { context.close(); open(); }

    private void open() {
        Instant previousTime = clock == null ? null : clock.instant();
        context = new SpringApplicationBuilder(MonitorApplication.class, ControlledClock.class).run("--server.port=0",
                "--monitor.scheduler.enabled=false", "--spring.datasource.url=" + db.getJdbcUrl(),
                "--spring.datasource.username=" + db.getUsername(), "--spring.datasource.password=" + db.getPassword(),
                "--ADMIN_USERNAME=" + UUID.randomUUID(), "--ADMIN_PASSWORD=" + UUID.randomUUID(),
                "--monitor.demo-origin=" + origin);
        jdbc = context.getBean(JdbcTemplate.class);
        monitors = context.getBean(MonitorService.class);
        store = context.getBean(ScheduleStore.class);
        checks = context.getBean(CheckRepository.class);
        incidents = context.getBean(IncidentRepository.class);
        processor = context.getBean(IncidentProcessor.class);
        clock = context.getBean(CheckSchedulerTest.MutableClock.class);
        if (previousTime != null) { clock.advance(Duration.between(clock.instant(), previousTime).getSeconds()); }
    }

    @TestConfiguration
    static class ControlledClock {
        @Bean @Primary
        CheckSchedulerTest.MutableClock lifecycleClock() { return new CheckSchedulerTest.MutableClock(); }
    }
}
