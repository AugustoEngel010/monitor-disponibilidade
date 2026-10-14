package br.com.monitor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.monitoring.ScheduleStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleStoreIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final Instant now = Instant.parse("2026-10-14T12:00:00Z");
    private final MonitorConfiguration config = MonitorConfiguration.validated("scheduled", "https://example.org",
            60, 5, 200, 3, 2, "ops@example.org");
    private JdbcTemplate jdbc;
    private MonitorRepository monitors;
    private CheckRepository results;
    private ScheduleStore store;

    @BeforeAll
    void start() {
        db.start();
        var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        var named = new NamedParameterJdbcTemplate(source);
        monitors = new MonitorRepository(named);
        results = new CheckRepository(named);
        store = new ScheduleStore(named, monitors, results, new DataSourceTransactionManager(source));
    }

    @BeforeEach
    void clear() { jdbc.execute("TRUNCATE monitor.check_attempts, monitor.check_results, monitor.monitors CASCADE"); }

    @AfterAll
    void stop() { db.stop(); }

    @Test
    void selectsOldestDueActiveWithStableIdTieAndLeavesExcessUnchanged() {
        var first = create(new UUID(0, 1), now.minusSeconds(120));
        var second = create(new UUID(0, 2), now.minusSeconds(120));
        var boundary = create(UUID.randomUUID(), now);
        var future = create(UUID.randomUUID(), now.plusSeconds(1));
        var paused = create(UUID.randomUUID(), now.minusSeconds(200)).pause(now);
        monitors.save(paused);
        var attempt = store.claim(now).orElseThrow();
        assertThat(attempt.monitor().id()).isEqualTo(first.id());
        assertThat(attempt.scheduledAt()).isEqualTo(now.minusSeconds(120));
        assertThat(attempt.startedAt()).isEqualTo(now);
        assertThat(monitors.find(first.id(), false).orElseThrow().state().nextCheckAt()).isEqualTo(now.plusSeconds(60));
        assertThat(monitors.find(second.id(), false)).contains(second);
        assertThat(store.claim(now).orElseThrow().monitor().id()).isEqualTo(second.id());
        assertThat(store.claim(now).orElseThrow().monitor().id()).isEqualTo(boundary.id());
        assertThat(store.claim(now)).isEmpty();
        assertThat(monitors.find(paused.id(), false)).contains(paused);
        assertThat(monitors.find(future.id(), false)).contains(future);
        assertThat(store.pending()).hasSize(3);
    }

    @Test
    void simultaneousClaimsAndSuccessiveRoundsCannotOverlapEvenAfterInterval() throws Exception {
        var monitor = create(UUID.randomUUID(), now);
        var barrier = new CyclicBarrier(2);
        Callable<Boolean> claim = () -> { barrier.await(); return store.claim(now).isPresent(); };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = pool.invokeAll(List.of(claim, claim));
            assertThat(List.of(calls.get(0).get(), calls.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(store.claim(now.plusSeconds(3600))).isEmpty();
        assertThat(store.pending()).hasSize(1);
        assertThat(monitors.find(monitor.id(), false).orElseThrow().state().nextCheckAt())
                .isEqualTo(now.plusSeconds(60));
    }

    @Test
    void failedReservationRollsBackSchedule() {
        var monitor = create(UUID.randomUUID(), now.minusSeconds(60));
        jdbc.execute("ALTER TABLE monitor.check_attempts ADD CONSTRAINT reject_claim CHECK (false)");
        try {
            assertThatThrownBy(() -> store.claim(now)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        finally { jdbc.execute("ALTER TABLE monitor.check_attempts DROP CONSTRAINT reject_claim"); }
        assertThat(monitors.find(monitor.id(), false)).contains(monitor);
        assertThat(store.pending()).isEmpty();
    }

    @Test
    void interruptionRecoveryPreservesStateHistoryAndScheduleAndIsIdempotent() {
        var monitor = create(UUID.randomUUID(), now.minusSeconds(60));
        var incident = UUID.randomUUID();
        IncidentFixtures.open(jdbc, monitor.id(), incident, 1);
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='DOWN', open_incident_id=?, recovery_count=1,"
                + " recovery_started_at=?, last_observed_at=?, current_observation=true WHERE id=?",
                incident, java.sql.Timestamp.from(now.minusSeconds(10)), java.sql.Timestamp.from(now.minusSeconds(10)),
                monitor.id());
        var attempt = store.claim(now).orElseThrow();
        var before = monitors.find(monitor.id(), false).orElseThrow();
        var recovered = new ScheduleStore(new NamedParameterJdbcTemplate(jdbc), monitors, results,
                new DataSourceTransactionManager(jdbc.getDataSource()));
        recovered.recover(attempt.monitor().id(), now.plusSeconds(600));
        recovered.recover(attempt.monitor().id(), now.plusSeconds(601));
        var result = results.find(attempt.executionId()).orElseThrow();
        assertThat(result.outcome()).isEqualTo(Outcome.CANCELLED);
        assertThat(result.outcome().external()).isFalse();
        assertThat(result.durationNanos()).isZero();
        assertThat(result.configurationVersion()).isEqualTo(1);
        assertThat(result.executionGeneration()).isEqualTo(1);
        assertThat(result.startedAt()).isEqualTo(now);
        assertThat(result.finishedAt()).isEqualTo(now.plusSeconds(600));
        var after = monitors.find(monitor.id(), false).orElseThrow();
        assertThat(after.configuration()).isEqualTo(before.configuration());
        assertThat(after.state().confirmedState()).isEqualTo("DOWN");
        assertThat(after.state().openIncidentId()).isEqualTo(incident);
        assertThat(after.state().lastObservedAt()).isEqualTo(before.state().lastObservedAt());
        assertThat(after.state().currentObservation()).isTrue();
        assertThat(after.state().recoveryCount()).isZero();
        assertThat(after.state().recoveryStartedAt()).isNull();
        assertThat(after.state().nextCheckAt()).isEqualTo(before.state().nextCheckAt());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.check_results", Integer.class)).isEqualTo(1);
        assertThat(store.pending()).isEmpty();
        var next = recovered.claim(now.plusSeconds(600)).orElseThrow();
        assertThat(next.executionId()).isNotEqualTo(attempt.executionId());
        assertThat(recovered.claim(now.plusSeconds(600))).isEmpty();
        assertThat(monitors.find(monitor.id(), false).orElseThrow().state().nextCheckAt())
                .isEqualTo(now.plusSeconds(660));
    }

    @Test
    void recordedResultWinsRecoveryIsAppliedOnceAndRestartWithoutAttemptPreservesState() {
        var monitor = create(UUID.randomUUID(), now);
        var attempt = store.claim(now).orElseThrow();
        var result = new CheckResult(monitor.id(), attempt.executionId(), 1, 1, now, now.plusSeconds(1),
                42, Outcome.SUCCESS, 200);
        results.save(result);
        jdbc.update("UPDATE monitor.monitors SET failure_count=2 WHERE id=?", monitor.id());
        store.recover(monitor.id(), now.plusSeconds(600));
        store.recover(monitor.id(), now.plusSeconds(601));
        assertThat(results.find(attempt.executionId())).contains(result);
        assertThat(monitors.find(monitor.id(), false).orElseThrow().state().failureCount()).isZero();
        assertThat(monitors.find(monitor.id(), false).orElseThrow().state().confirmedState()).isEqualTo("UP");
        var after = monitors.find(monitor.id(), false).orElseThrow();
        store.recover(monitor.id(), now.plusSeconds(999));
        assertThat(monitors.find(monitor.id(), false)).contains(after);
        assertThat(store.pending()).isEmpty();
    }

    @Test
    void pauseResumeAndPolicyEditDoNotOverwriteTheirScheduleOrReleaseOldReservation() {
        var original = create(UUID.randomUUID(), now);
        var attempt = store.claim(now).orElseThrow();
        var resumed = monitors.find(original.id(), false).orElseThrow().pause(now).resume(now.plusSeconds(1));
        monitors.save(resumed);
        assertThat(store.current(attempt)).isFalse();
        assertThat(store.claim(now.plusSeconds(120))).isEmpty();
        store.recover(original.id(), now.plusSeconds(2));
        assertThat(monitors.find(original.id(), false)).contains(resumed);
        assertThat(store.claim(now.plusSeconds(2))).isPresent();
    }

    @Test
    void finishOnlyRemovesItsOwnExecutionAndPreservesPausedState() {
        var monitor = create(UUID.randomUUID(), now);
        var attempt = store.claim(now).orElseThrow();
        var paused = monitors.find(monitor.id(), false).orElseThrow().pause(now.plusSeconds(1));
        monitors.save(paused);
        store.finish(attempt, new CheckResult(monitor.id(), attempt.executionId(), 1, 1, now, now.plusSeconds(2),
                12, Outcome.SUCCESS, 200));
        assertThat(monitors.find(monitor.id(), false)).contains(paused);
        assertThat(results.find(attempt.executionId()).orElseThrow().outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(store.pending()).isEmpty();
    }

    private Monitor create(UUID id, Instant scheduled) {
        var monitor = Monitor.create(id, config, scheduled);
        monitors.insert(monitor);
        return monitor;
    }
}
