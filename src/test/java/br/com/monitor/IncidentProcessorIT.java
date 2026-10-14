package br.com.monitor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.incident.IncidentProcessor;
import br.com.monitor.incident.IncidentRepository;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncidentProcessorIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final Instant base = Instant.parse("2026-10-14T12:00:00Z");
    private final MonitorConfiguration config = MonitorConfiguration.validated("processor", "https://example.org",
            60, 5, 200, 3, 2, "ops@example.org");
    private JdbcTemplate jdbc;
    private MonitorRepository monitors;
    private CheckRepository checks;
    private IncidentRepository incidents;
    private IncidentProcessor processor;
    private TransactionTemplate transaction;
    private NamedParameterJdbcTemplate named;
    private DataSourceTransactionManager manager;
    private Monitor monitor;

    @BeforeAll
    void start() {
        db.start();
        var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        named = new NamedParameterJdbcTemplate(source);
        monitors = new MonitorRepository(named);
        checks = new CheckRepository(named);
        incidents = new IncidentRepository(named);
        manager = new DataSourceTransactionManager(source);
        transaction = new TransactionTemplate(manager);
        processor = new IncidentProcessor(monitors, checks, incidents, named, manager);
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE monitor.monitors CASCADE");
        monitor = Monitor.create(UUID.randomUUID(), config, base);
        monitors.insert(monitor);
    }

    @AfterAll
    void stop() { db.stop(); }

    @Test
    void confirmsThirdFailureAndSecondSuccessWithAllFourTimesAndDurableEvidence() {
        // INC-01/03/04/08: C-02/C-17, exact evidence in every durable payload.
        apply(Outcome.HTTP_FAILURE, 0);
        assertThat(state().failureCount()).isEqualTo(1);
        assertThat(state().confirmedState()).isEqualTo("UNKNOWN");
        assertThat(count("incidents")).isZero();
        apply(Outcome.TIMEOUT, 60);
        assertThat(state().failureCount()).isEqualTo(2);
        assertThat(count("state_transitions")).isZero();
        var third = apply(Outcome.TLS_FAILURE, 120);
        var id = state().openIncidentId();
        assertThat(state().confirmedState()).isEqualTo("DOWN");
        assertThat(state().failureCount()).isEqualTo(3);
        assertThat(count("incidents")).isEqualTo(1);
        var down = incidents.find(id).orElseThrow();
        assertThat(down.firstFailureAt()).isEqualTo(base);
        assertThat(down.downConfirmedAt()).isEqualTo(base.plusSeconds(120));
        assertThat(down.recoveryStartedAt()).isNull();
        assertThat(down.recoveredAt()).isNull();
        assertTransition(third, id, "DOWN", 120);
        var downIntent = jdbc.queryForMap("SELECT * FROM monitor.notification_intents");
        for (int i = 0; i < 10; i++) { apply(Outcome.HTTP_FAILURE, 120); }
        assertThat(state().openIncidentId()).isEqualTo(id);
        assertThat(count("incidents")).isEqualTo(1);
        assertThat(count("state_transitions")).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT * FROM monitor.notification_intents")).containsExactly(downIntent);
        apply(Outcome.SUCCESS, 180);
        assertThat(state().confirmedState()).isEqualTo("DOWN");
        assertThat(state().recoveryCount()).isEqualTo(1);
        assertThat(incidents.find(id).orElseThrow().recoveryStartedAt()).isEqualTo(base.plusSeconds(180));
        var last = apply(Outcome.SUCCESS, 240);
        assertThat(state().confirmedState()).isEqualTo("UP");
        assertThat(state().openIncidentId()).isNull();
        assertThat(state().recoveryCount()).isEqualTo(2);
        var closed = incidents.find(id).orElseThrow();
        assertThat(closed.firstFailureAt()).isEqualTo(base);
        assertThat(closed.downConfirmedAt()).isEqualTo(base.plusSeconds(120));
        assertThat(closed.recoveryStartedAt()).isEqualTo(base.plusSeconds(180));
        assertThat(closed.recoveredAt()).isEqualTo(base.plusSeconds(240));
        assertThat(java.time.Duration.between(closed.firstFailureAt(), closed.recoveryStartedAt()).getSeconds())
                .isEqualTo(180);
        assertTransition(last, id, "RECOVERY", 240);
        var recoveryIntent = jdbc.queryForMap("SELECT i.* FROM monitor.notification_intents i"
                + " JOIN monitor.state_transitions t ON t.id=i.transition_id WHERE t.type='RECOVERY'");
        assertThat(recoveryIntent).containsEntry("recipient", config.alertRecipient())
                .containsEntry("monitor_name", config.name()).containsEntry("monitor_url", config.url())
                .containsEntry("first_failure_at", Timestamp.from(base))
                .containsEntry("down_confirmed_at", Timestamp.from(base.plusSeconds(120)))
                .containsEntry("recovery_started_at", Timestamp.from(base.plusSeconds(180)))
                .containsEntry("recovered_at", Timestamp.from(base.plusSeconds(240)))
                .containsEntry("status", "PENDING");
        assertThat(count("state_transitions")).isEqualTo(2);
        assertThat(count("notification_intents")).isEqualTo(2);
        assertThat(count("result_applications")).isEqualTo(15);
    }

    @Test
    void successBeforeLimitInterruptsAndFirstSuccessNeverEmitsRecovery() {
        apply(Outcome.SUCCESS, 0);
        apply(Outcome.HTTP_FAILURE, 60);
        apply(Outcome.HTTP_FAILURE, 120);
        apply(Outcome.SUCCESS, 180);
        assertThat(state().failureCount()).isZero();
        assertThat(state().firstFailureAt()).isNull();
        assertThat(state().confirmedState()).isEqualTo("UP");
        assertThat(count("incidents")).isZero();
        assertThat(count("state_transitions")).isZero();
        assertThat(count("notification_intents")).isZero();
        apply(Outcome.HTTP_FAILURE, 240);
        assertThat(state().failureCount()).isEqualTo(1);
        assertThat(state().firstFailureAt()).isEqualTo(base.plusSeconds(240));
    }

    @Test
    void failureDuringRecoveryClearsProvisionalTimeAndFinalSequenceDefinesClosure() {
        down();
        var id = state().openIncidentId();
        apply(Outcome.SUCCESS, 180);
        apply(Outcome.DNS_FAILURE, 240);
        assertThat(state().recoveryCount()).isZero();
        assertThat(incidents.find(id).orElseThrow().recoveryStartedAt()).isNull();
        assertThat(incidents.find(id).orElseThrow().recoveredAt()).isNull();
        assertThat(state().openIncidentId()).isEqualTo(id);
        apply(Outcome.SUCCESS, 300);
        apply(Outcome.SUCCESS, 360);
        assertThat(incidents.find(id).orElseThrow().recoveryStartedAt()).isEqualTo(base.plusSeconds(300));
        assertThat(incidents.find(id).orElseThrow().recoveredAt()).isEqualTo(base.plusSeconds(360));
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"INTERNAL_ERROR", "DESTINATION_BLOCKED", "CANCELLED"})
    void nonExternalInterruptsFailureAndRecoveryWithoutChangingExternalObservation(Outcome outcome) {
        apply(Outcome.HTTP_FAILURE, 0);
        apply(Outcome.HTTP_FAILURE, 60);
        apply(outcome, 120);
        assertThat(state().failureCount()).isZero();
        assertThat(state().firstFailureAt()).isNull();
        assertThat(state().lastObservedAt()).isEqualTo(base.plusSeconds(60));
        assertThat(state().confirmedState()).isEqualTo("UNKNOWN");
        assertThat(count("incidents")).isZero();
        down();
        var id = state().openIncidentId();
        apply(Outcome.SUCCESS, 180);
        apply(outcome, 240);
        assertThat(state().confirmedState()).isEqualTo("DOWN");
        assertThat(state().recoveryCount()).isZero();
        assertThat(state().lastObservedAt()).isEqualTo(base.plusSeconds(180));
        assertThat(state().currentObservation()).isTrue();
        assertThat(state().openIncidentId()).isEqualTo(id);
        assertThat(incidents.find(id).orElseThrow().recoveryStartedAt()).isNull();
        assertThat(incidents.find(id).orElseThrow().recoveredAt()).isNull();
        assertThat(count("state_transitions")).isEqualTo(1);
    }

    @Test
    void sameOpeningAndClosingResultsPreserveEveryDurableFieldEvenWithDifferentPayload() {
        apply(Outcome.HTTP_FAILURE, 0);
        apply(Outcome.HTTP_FAILURE, 60);
        var third = apply(Outcome.HTTP_FAILURE, 120);
        var before = snapshot();
        assertThat(processor.process(third.executionId())).isFalse();
        assertThat(checks.save(new CheckResult(monitor.id(), third.executionId(), 1, 1, base.plusSeconds(999),
                base.plusSeconds(999), 999, Outcome.SUCCESS, 200))).isEqualTo(third);
        assertThat(processor.process(third.executionId())).isFalse();
        assertThat(snapshot()).isEqualTo(before);
        apply(Outcome.SUCCESS, 180);
        var last = apply(Outcome.SUCCESS, 240);
        before = snapshot();
        assertThat(processor.process(last.executionId())).isFalse();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void concurrentReplayOfThresholdResultAppliesOnce() throws Exception {
        apply(Outcome.HTTP_FAILURE, 0);
        apply(Outcome.HTTP_FAILURE, 60);
        var result = save(Outcome.HTTP_FAILURE, 120);
        var barrier = new CyclicBarrier(4);
        Callable<Boolean> process = () -> { barrier.await(); return processor.process(result.executionId()); };
        try (var pool = Executors.newFixedThreadPool(4)) {
            var calls = pool.invokeAll(List.of(process, process, process, process));
            assertThat(List.of(calls.get(0).get(), calls.get(1).get(), calls.get(2).get(), calls.get(3).get()))
                    .containsExactlyInAnyOrder(true, false, false, false);
        }
        assertThat(state().failureCount()).isEqualTo(3);
        assertThat(count("incidents")).isEqualTo(1);
        assertThat(count("state_transitions")).isEqualTo(1);
        assertThat(count("notification_intents")).isEqualTo(1);
        assertThat(count("result_applications")).isEqualTo(3);
        var before = snapshot();
        assertThat(processor.process(result.executionId())).isFalse();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void concurrentDistinctFailuresKeepOneIncidentAndOneDownTransition() throws Exception {
        apply(Outcome.HTTP_FAILURE, 0);
        apply(Outcome.HTTP_FAILURE, 60);
        var first = save(Outcome.HTTP_FAILURE, 120);
        var second = save(Outcome.CONNECTION_FAILURE, 120);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = pool.invokeAll(List.<Callable<Boolean>>of(
                    () -> { barrier.await(); return processor.process(first.executionId()); },
                    () -> { barrier.await(); return processor.process(second.executionId()); }));
            assertThat(calls.get(0).get()).isTrue();
            assertThat(calls.get(1).get()).isTrue();
        }
        assertThat(state().failureCount()).isEqualTo(3);
        assertThat(count("incidents")).isEqualTo(1);
        assertThat(count("state_transitions")).isEqualTo(1);
        assertThat(count("notification_intents")).isEqualTo(1);
        assertThat(count("result_applications")).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"incidents", "state_transitions", "notification_intents", "result_applications"})
    void failureAtAnyOpeningWriteRollsBackAllEffectsAndAllowsOriginalResultRetry(String table) {
        apply(Outcome.HTTP_FAILURE, 0);
        apply(Outcome.HTTP_FAILURE, 60);
        var third = save(Outcome.HTTP_FAILURE, 120);
        var before = snapshot();
        reject(table);
        try {
            assertThatThrownBy(() -> processor.process(third.executionId())).isInstanceOf(DataAccessException.class);
        }
        finally { allow(table); }
        assertThat(snapshot()).isEqualTo(before);
        assertThat(incidents.processed(third.executionId())).isFalse();
        assertThat(checks.find(third.executionId())).contains(third);
        assertThat(processor.process(third.executionId())).isTrue();
        assertThat(state().confirmedState()).isEqualTo("DOWN");
        assertThat(incidents.find(state().openIncidentId()).orElseThrow().downConfirmedAt())
                .isEqualTo(base.plusSeconds(120));
        assertThat(count("state_transitions")).isEqualTo(1);
        assertThat(count("notification_intents")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"incidents", "state_transitions", "notification_intents", "result_applications"})
    void failureAtAnyClosingWritePreservesOpenIncidentAndAllowsRetry(String table) {
        down();
        var id = state().openIncidentId();
        apply(Outcome.SUCCESS, 180);
        var last = save(Outcome.SUCCESS, 240);
        var before = snapshot();
        reject(table);
        try { assertThatThrownBy(() -> processor.process(last.executionId())).isInstanceOf(DataAccessException.class); }
        finally { allow(table); }
        assertThat(snapshot()).isEqualTo(before);
        assertThat(state().openIncidentId()).isEqualTo(id);
        assertThat(incidents.find(id).orElseThrow().recoveredAt()).isNull();
        assertThat(processor.process(last.executionId())).isTrue();
        assertThat(incidents.find(id).orElseThrow().recoveryStartedAt()).isEqualTo(base.plusSeconds(180));
        assertThat(incidents.find(id).orElseThrow().recoveredAt()).isEqualTo(base.plusSeconds(240));
        assertThat(state().confirmedState()).isEqualTo("UP");
        assertThat(count("state_transitions")).isEqualTo(2);
        assertThat(count("notification_intents")).isEqualTo(2);
    }

    @Test
    void readersCannotSeeUncommittedTransitionAndOuterRollbackUndoesApplication() {
        apply(Outcome.HTTP_FAILURE, 0);
        apply(Outcome.HTTP_FAILURE, 60);
        var third = save(Outcome.HTTP_FAILURE, 120);
        var before = snapshot();
        transaction.executeWithoutResult(status -> {
            assertThat(processor.process(third.executionId())).isTrue();
            try (var reader = Executors.newSingleThreadExecutor()) {
                assertThat(reader.submit(this::snapshot).get()).isEqualTo(before);
            } catch (Exception error) { throw new IllegalStateException(error); }
            status.setRollbackOnly();
        });
        assertThat(snapshot()).isEqualTo(before);
        assertThat(processor.process(third.executionId())).isTrue();
        assertThat(state().confirmedState()).isEqualTo("DOWN");
    }

    @Test
    void earlierResultAndOldSnapshotAreHistoryOnlyAndCannotOverwriteCurrentState() {
        var latest = apply(Outcome.SUCCESS, 60);
        var before = monitors.find(monitor.id(), false).orElseThrow();
        var earlier = save(Outcome.HTTP_FAILURE, 0);
        assertThat(processor.process(earlier.executionId())).isFalse();
        assertThat(monitors.find(monitor.id(), false)).contains(before);
        assertThat(jdbc.queryForObject("SELECT disposition FROM monitor.result_applications WHERE execution_id=?",
                String.class, earlier.executionId())).isEqualTo("HISTORY_ONLY");
        var obsolete = checks.save(new CheckResult(monitor.id(), UUID.randomUUID(), 1, 2, base.plusSeconds(120),
                base.plusSeconds(120), 0, Outcome.HTTP_FAILURE, 503));
        assertThat(processor.process(obsolete.executionId())).isFalse();
        assertThat(monitors.find(monitor.id(), false)).contains(before);
        assertThat(checks.find(latest.executionId())).contains(latest);
        assertThat(count("incidents")).isZero();
    }

    @Test
    void logicalReservationStartControlsGapAndReconstructedProcessorKeepsCounters() {
        apply(Outcome.HTTP_FAILURE, 0);
        apply(Outcome.HTTP_FAILURE, 60);
        var result = save(Outcome.HTTP_FAILURE, 190);
        jdbc.update("INSERT INTO monitor.check_attempts VALUES (?, ?, 1, 1, ?, ?)", monitor.id(), result.executionId(),
                Timestamp.from(base.plusSeconds(120)), Timestamp.from(base.plusSeconds(185)));
        var restarted = new IncidentProcessor(new MonitorRepository(named), new CheckRepository(named),
                new IncidentRepository(named), named, manager);
        assertThat(restarted.process(result.executionId())).isTrue();
        assertThat(state().confirmedState()).isEqualTo("DOWN");
        assertThat(incidents.find(state().openIncidentId()).orElseThrow().firstFailureAt()).isEqualTo(base);
        assertThat(incidents.find(state().openIncidentId()).orElseThrow().downConfirmedAt())
                .isEqualTo(base.plusSeconds(190));
    }

    private void down() {
        // Increasing times also work after the interrupted sequence in the non-external scenarios.
        apply(Outcome.HTTP_FAILURE, 120);
        apply(Outcome.HTTP_FAILURE, 120);
        apply(Outcome.HTTP_FAILURE, 120);
    }

    private CheckResult apply(Outcome outcome, int seconds) {
        var result = save(outcome, seconds);
        assertThat(processor.process(result.executionId())).isTrue();
        return result;
    }

    private CheckResult save(Outcome outcome, int seconds) {
        return checks.save(new CheckResult(monitor.id(), UUID.randomUUID(), 1, 1, base.plusSeconds(seconds),
                base.plusSeconds(seconds), 0, outcome, outcome == Outcome.SUCCESS ? Integer.valueOf(200)
                        : outcome == Outcome.HTTP_FAILURE ? Integer.valueOf(503) : null));
    }

    private Monitor.State state() { return monitors.find(monitor.id(), false).orElseThrow().state(); }
    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM monitor." + table, Integer.class);
    }

    private List<List<Map<String, Object>>> snapshot() {
        return List.of(jdbc.queryForList("SELECT * FROM monitor.monitors ORDER BY id"),
                jdbc.queryForList("SELECT * FROM monitor.incidents ORDER BY id"),
                jdbc.queryForList("SELECT * FROM monitor.state_transitions ORDER BY id"),
                jdbc.queryForList("SELECT * FROM monitor.notification_intents ORDER BY id"),
                jdbc.queryForList("SELECT * FROM monitor.result_applications ORDER BY execution_id"),
                jdbc.queryForList("SELECT * FROM monitor.check_results ORDER BY execution_id"));
    }

    private void assertTransition(CheckResult result, UUID id, String type, int seconds) {
        assertThat(jdbc.queryForMap("SELECT * FROM monitor.state_transitions WHERE execution_id=?",
                result.executionId()))
                .containsEntry("execution_id", result.executionId()).containsEntry("monitor_id", monitor.id())
                .containsEntry("incident_id", id).containsEntry("configuration_version", 1L).containsEntry("type", type)
                .containsEntry("confirmed_at", Timestamp.from(base.plusSeconds(seconds)));
    }

    private void reject(String table) {
        jdbc.execute("ALTER TABLE monitor." + table + " ADD CONSTRAINT reject_application CHECK (false) NOT VALID");
    }

    private void allow(String table) {
        jdbc.execute("ALTER TABLE monitor." + table + " DROP CONSTRAINT reject_application");
    }
}
