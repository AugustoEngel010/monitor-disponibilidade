package br.com.monitor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.incident.IncidentRepository;
import br.com.monitor.incident.IncidentStateMachine;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IncidentSchemaIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final Instant now = Instant.parse("2026-10-14T12:00:00Z");
    private final MonitorConfiguration config = MonitorConfiguration.validated("schema", "https://example.org",
            60, 5, 200, 3, 2, "ops@example.org");
    private JdbcTemplate jdbc;
    private MonitorRepository monitors;
    private IncidentRepository incidents;
    private CheckRepository checks;
    private TransactionTemplate transaction;

    @BeforeAll
    void start() {
        db.start();
        var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        var named = new NamedParameterJdbcTemplate(source);
        monitors = new MonitorRepository(named);
        incidents = new IncidentRepository(named);
        checks = new CheckRepository(named);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
    }

    @BeforeEach
    void clear() { jdbc.execute("TRUNCATE monitor.monitors CASCADE"); }

    @AfterAll
    void stop() { db.stop(); }

    @Test
    void partialUniqueIndexRejectsSecondOpenButAllowsNewIncidentAfterClosure() {
        // INC-03/08: uniqueness is enforced by PostgreSQL, independently of service locks.
        var monitor = create();
        var first = UUID.randomUUID();
        IncidentFixtures.open(jdbc, monitor.id(), first, 1);
        assertThatThrownBy(() -> IncidentFixtures.open(jdbc, monitor.id(), UUID.randomUUID(), 1))
                .isInstanceOf(DataAccessException.class);
        incidents.close(first, now, now.plusSeconds(60));
        var second = UUID.randomUUID();
        IncidentFixtures.open(jdbc, monitor.id(), second, 1);
        assertThat(incidents.find(first).orElseThrow().recoveredAt()).isEqualTo(now.plusSeconds(60));
        assertThat(incidents.find(second).orElseThrow().recoveredAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.incidents", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentDirectInsertsStillProduceOnlyOneOpenIncident() throws Exception {
        var monitor = create();
        var barrier = new CyclicBarrier(2);
        Callable<Boolean> insert = () -> {
            barrier.await();
            try { IncidentFixtures.open(jdbc, monitor.id(), UUID.randomUUID(), 1); return true; }
            catch (DataAccessException error) { return false; }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = pool.invokeAll(List.of(insert, insert));
            assertThat(List.of(calls.get(0).get(), calls.get(1).get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.incidents WHERE recovered_at IS NULL",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void monitorCannotReferenceMissingIncidentOrAnotherMonitorsIncident() {
        var monitor = create();
        var other = create();
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> reference(monitor.id(), id)).isInstanceOf(DataAccessException.class);
        IncidentFixtures.open(jdbc, other.id(), id, 1);
        assertThatThrownBy(() -> reference(monitor.id(), id)).isInstanceOf(DataAccessException.class);
        assertThat(monitors.find(monitor.id(), false)).contains(monitor);
    }

    @Test
    void repositoryPersistsFourTimesTransitionIdentityAndFrozenIntent() {
        var monitor = create();
        var result = failure(monitor, UUID.randomUUID(), now.plusSeconds(120));
        checks.save(result);
        UUID incidentId = UUID.randomUUID();
        var state = new Monitor.State("ACTIVE", "DOWN", 3, 0, now, null, result.finishedAt(), true,
                incidentId, 1, 1, monitor.state().nextCheckAt());
        var down = new Monitor(monitor.id(), config, state, monitor.createdAt(), result.finishedAt());
        transaction.executeWithoutResult(status -> {
            monitors.save(down);
            incidents.open(down, result.finishedAt());
            incidents.mark(result, now.plusSeconds(119), true);
            incidents.transition(down, result, incidentId, IncidentStateMachine.Transition.DOWN);
        });
        var incident = incidents.find(incidentId).orElseThrow();
        assertThat(incident.id()).isEqualTo(incidentId);
        assertThat(incident.monitorId()).isEqualTo(monitor.id());
        assertThat(incident.configurationVersion()).isEqualTo(1);
        assertThat(incident.firstFailureAt()).isEqualTo(now);
        assertThat(incident.downConfirmedAt()).isEqualTo(now.plusSeconds(120));
        assertThat(incident.recoveryStartedAt()).isNull();
        assertThat(incident.recoveredAt()).isNull();
        assertThat(incidents.processed(result.executionId())).isTrue();
        assertThat(incidents.lastAppliedStart(result)).contains(now.plusSeconds(119));
        var transition = jdbc.queryForMap("SELECT * FROM monitor.state_transitions");
        assertThat(transition).containsEntry("execution_id", result.executionId())
                .containsEntry("monitor_id", monitor.id()).containsEntry("incident_id", incidentId)
                .containsEntry("configuration_version", 1L).containsEntry("type", "DOWN")
                .containsEntry("confirmed_at", Timestamp.from(result.finishedAt()));
        var intent = jdbc.queryForMap("SELECT * FROM monitor.notification_intents");
        assertThat(intent).containsEntry("transition_id", transition.get("id"))
                .containsEntry("recipient", "ops@example.org").containsEntry("monitor_name", "schema")
                .containsEntry("monitor_url", "https://example.org").containsEntry("status", "PENDING")
                .containsEntry("first_failure_at", Timestamp.from(now))
                .containsEntry("down_confirmed_at", Timestamp.from(result.finishedAt()))
                .containsEntry("recovery_started_at", null).containsEntry("recovered_at", null);
        incidents.recoveryStarted(incidentId, now.plusSeconds(180));
        assertThat(incidents.find(incidentId).orElseThrow().recoveryStartedAt()).isEqualTo(now.plusSeconds(180));
        incidents.close(incidentId, now.plusSeconds(180), now.plusSeconds(240));
        incidents.recoveryStarted(incidentId, null);
        assertThat(incidents.find(incidentId).orElseThrow().recoveryStartedAt()).isEqualTo(now.plusSeconds(180));
        assertThat(incidents.find(incidentId).orElseThrow().recoveredAt()).isEqualTo(now.plusSeconds(240));
        assertThatThrownBy(() -> incidents.mark(result, now.plusSeconds(999), true))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> incidents.transition(down, result, incidentId, IncidentStateMachine.Transition.DOWN))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForList("SELECT * FROM monitor.notification_intents")).containsExactly(intent);
    }

    @Test
    void mismatchedTransitionSnapshotIsRejectedWithoutIntent() {
        var monitor = create();
        var other = create();
        var id = UUID.randomUUID();
        IncidentFixtures.open(jdbc, other.id(), id, 1);
        var result = checks.save(failure(monitor, UUID.randomUUID(), now));
        assertThatThrownBy(() -> incidents.transition(monitor, result, id, IncidentStateMachine.Transition.DOWN))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.state_transitions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.notification_intents", Integer.class)).isZero();
    }

    @Test
    void v4UpgradePreservesHistoryAndExcludesCompletedChecksFromRetroactiveApplication() {
        // INC-07/08: completed legacy checks do not open retroactive incidents.
        try (var previous = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            previous.start();
            var source = new DriverManagerDataSource(previous.getJdbcUrl(),
                    previous.getUsername(), previous.getPassword());
            Flyway.configure().dataSource(source).target("4").load().migrate();
            var sql = new JdbcTemplate(source);
            var named = new NamedParameterJdbcTemplate(source);
            var repository = new MonitorRepository(named);
            var monitor = Monitor.create(UUID.randomUUID(), config, now);
            repository.insert(monitor);
            var completed = new CheckRepository(named).save(failure(monitor, UUID.randomUUID(), now));
            var pending = new CheckRepository(named).save(failure(monitor, UUID.randomUUID(), now.plusSeconds(60)));
            sql.update("INSERT INTO monitor.check_attempts VALUES (?, ?, 1, 1, ?, ?)", monitor.id(),
                    pending.executionId(), Timestamp.from(now), Timestamp.from(now.plusSeconds(60)));
            var history = sql.queryForList("SELECT * FROM public.flyway_schema_history ORDER BY installed_rank");
            var results = sql.queryForList("SELECT * FROM monitor.check_results ORDER BY execution_id");
            var flyway = Flyway.configure().dataSource(source).target("5").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(sql.queryForList("SELECT * FROM public.flyway_schema_history WHERE version IN ('1','2','3','4')"
                    + " ORDER BY installed_rank")).isEqualTo(history);
            assertThat(sql.queryForList("SELECT * FROM monitor.check_results ORDER BY execution_id"))
                    .isEqualTo(results);
            assertThat(repository.find(monitor.id(), false)).contains(monitor);
            assertThat(sql.queryForMap("SELECT * FROM monitor.result_applications"))
                    .containsEntry("execution_id", completed.executionId())
                    .containsEntry("disposition", "HISTORY_ONLY");
            assertThat(new IncidentRepository(named).processed(pending.executionId())).isFalse();
            assertThat(sql.queryForObject("SELECT count(*) FROM monitor.incidents", Integer.class)).isZero();
        }
    }

    private Monitor create() {
        var monitor = Monitor.create(UUID.randomUUID(), config, now);
        monitors.insert(monitor);
        return monitor;
    }

    private void reference(UUID monitor, UUID incident) {
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='DOWN', open_incident_id=? WHERE id=?",
                incident, monitor);
    }

    private CheckResult failure(Monitor monitor, UUID id, Instant finish) {
        return new CheckResult(monitor.id(), id, 1, 1, finish, finish, 0, CheckResult.Outcome.HTTP_FAILURE, 503);
    }
}
