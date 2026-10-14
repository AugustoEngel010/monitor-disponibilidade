package br.com.monitor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import br.com.monitor.administration.MonitorRepository;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckRepositoryIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private JdbcTemplate jdbc;
    private CheckRepository repository;
    private UUID monitorId;

    @BeforeAll
    void start() {
        db.start();
        Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .defaultSchema("public").load().migrate();
        jdbc = sql();
        repository = new CheckRepository(new NamedParameterJdbcTemplate(jdbc));
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE monitor.check_attempts, monitor.check_results, monitor.monitors CASCADE");
        var monitor = HttpCheckExecutorIT.monitor("https://example.org", 200);
        monitorId = monitor.id();
        new MonitorRepository(new NamedParameterJdbcTemplate(jdbc)).insert(monitor);
    }

    @AfterAll
    void stop() { db.stop(); }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void persistsExactMetadataAndSanitizedMessageWithoutBody(Outcome outcome) {
        var result = result(UUID.randomUUID(), outcome);
        assertThat(repository.save(result)).isEqualTo(result);
        assertThat(repository.find(result.executionId())).contains(result);
        var row = jdbc.queryForMap("SELECT * FROM monitor.check_results WHERE execution_id=?", result.executionId());
        assertThat(row).containsOnlyKeys("execution_id", "monitor_id", "configuration_version", "execution_generation",
                "started_at", "finished_at", "duration_nanos", "outcome", "http_status", "error_message");
        assertThat(row.get("execution_id")).isEqualTo(result.executionId());
        assertThat(row.get("monitor_id")).isEqualTo(monitorId);
        assertThat(row.get("configuration_version")).isEqualTo(3L);
        assertThat(row.get("execution_generation")).isEqualTo(7L);
        assertThat(((java.sql.Timestamp) row.get("started_at")).toInstant()).isEqualTo(result.startedAt());
        assertThat(((java.sql.Timestamp) row.get("finished_at")).toInstant()).isEqualTo(result.finishedAt());
        assertThat(row.get("duration_nanos")).isEqualTo(123456789L);
        assertThat(row.get("outcome")).isEqualTo(outcome.name());
        assertThat(row.get("http_status")).isEqualTo(result.httpStatus());
        assertThat(row.get("error_message")).isEqualTo(result.errorMessage());
    }

    @Test
    void repeatedSavePreservesFirstObservationEvenWithDifferentMetadata() {
        var first = result(UUID.randomUUID(), Outcome.SUCCESS);
        var duplicate = new CheckResult(monitorId, first.executionId(), 3, 7, first.startedAt().plusSeconds(1),
                first.finishedAt().plusSeconds(2), 987654321, Outcome.HTTP_FAILURE, 503);
        assertThat(repository.save(first)).isEqualTo(first);
        assertThat(repository.save(duplicate)).isEqualTo(first);
        assertThat(repository.save(first)).isEqualTo(first);
        assertThat(repository.find(first.executionId())).contains(first);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void concurrentSaveReturnsOneIdenticalWinner() throws Exception {
        var id = UUID.randomUUID();
        var success = result(id, Outcome.SUCCESS);
        var failure = result(id, Outcome.CONNECTION_FAILURE);
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Callable<CheckResult> a = () -> { started.countDown(); release.await(); return repository.save(success); };
        Callable<CheckResult> b = () -> { started.countDown(); release.await(); return repository.save(failure); };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(a);
            var second = pool.submit(b);
            assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            release.countDown();
            var winner = first.get();
            assertThat(winner).isIn(success, failure);
            assertThat(second.get()).isEqualTo(winner);
            assertThat(repository.find(id)).contains(winner);
        } finally { release.countDown(); }
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void executionIdCannotBeReusedForAnotherMonitorVersionOrGeneration() {
        var first = result(UUID.randomUUID(), Outcome.SUCCESS);
        repository.save(first);
        var other = HttpCheckExecutorIT.monitor("https://other.example", 200);
        new MonitorRepository(new NamedParameterJdbcTemplate(jdbc)).insert(other);
        for (var identity : List.of(new CheckResult(other.id(), first.executionId(), 3, 7,
                first.startedAt(), first.finishedAt(), 1, Outcome.SUCCESS, 200),
                new CheckResult(monitorId, first.executionId(), 4, 7,
                        first.startedAt(), first.finishedAt(), 1, Outcome.SUCCESS, 200),
                new CheckResult(monitorId, first.executionId(), 3, 8,
                        first.startedAt(), first.finishedAt(), 1, Outcome.SUCCESS, 200))) {
            assertThatThrownBy(() -> repository.save(identity)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Execution identifier belongs to another snapshot");
        }
        assertThat(repository.find(first.executionId())).contains(first);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void foreignKeyAndMetadataConstraintsRejectPartialRecordsAndAllowRetry() {
        var valid = result(UUID.randomUUID(), Outcome.SUCCESS);
        var unknown = new CheckResult(UUID.randomUUID(), valid.executionId(), 3, 7,
                valid.startedAt(), valid.finishedAt(), 123456789, Outcome.SUCCESS, 200);
        assertThatThrownBy(() -> repository.save(unknown)).isInstanceOf(DataAccessException.class);
        assertThat(repository.find(valid.executionId())).isEmpty();
        var negative = new CheckResult(monitorId, valid.executionId(), 3, 7,
                valid.startedAt(), valid.finishedAt(), -1, Outcome.SUCCESS, 200);
        assertThatThrownBy(() -> repository.save(negative)).isInstanceOf(DataAccessException.class);
        assertThat(count()).isZero();
        assertThat(repository.save(valid)).isEqualTo(valid);
        assertThatThrownBy(() -> jdbc.update("UPDATE monitor.check_results SET error_message='raw-secret'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE monitor.check_results SET http_status=null"))
                .isInstanceOf(DataAccessException.class);
        assertThat(repository.find(valid.executionId())).contains(valid);
    }

    @Test
    void missingExecutionIsEmptyAndNewConnectionSeesDurableResult() {
        var result = result(UUID.randomUUID(), Outcome.TIMEOUT);
        assertThat(repository.find(result.executionId())).isEmpty();
        repository.save(result);
        var reopened = new CheckRepository(new NamedParameterJdbcTemplate(sql()));
        assertThat(reopened.find(result.executionId())).contains(result);
        assertThat(reopened.save(result)).isEqualTo(result);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void v2DatabaseReceivesV3OnceWithoutChangingPriorData() {
        try (var previous = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            previous.start();
            var source = new DriverManagerDataSource(previous.getJdbcUrl(),
                    previous.getUsername(), previous.getPassword());
            var old = Flyway.configure().dataSource(source).defaultSchema("public").target("2").load();
            assertThat(old.migrate().migrationsExecuted).isEqualTo(2);
            var beforeSql = new JdbcTemplate(source);
            var configuration = HttpCheckExecutorIT.monitor("https://example.org", 200).configuration();
            var monitor = br.com.monitor.administration.Monitor.create(UUID.randomUUID(), configuration,
                    Instant.parse("2026-10-13T12:00:00Z"));
            var monitors = new MonitorRepository(new NamedParameterJdbcTemplate(beforeSql));
            monitors.insert(monitor);
            var before = beforeSql.queryForList("SELECT * FROM public.flyway_schema_history ORDER BY installed_rank");
            var current = Flyway.configure().dataSource(source).defaultSchema("public").target("3").load();
            assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(current.migrate().migrationsExecuted).isZero();
            assertThat(beforeSql.queryForList("SELECT * FROM public.flyway_schema_history WHERE version IN ('1','2')"
                    + " ORDER BY installed_rank")).isEqualTo(before);
            assertThat(monitors.find(monitor.id(), false)).contains(monitor);
            assertThat(beforeSql.queryForObject("SELECT count(*) FROM monitor.check_results", Long.class)).isZero();
        }
    }

    private CheckResult result(UUID executionId, Outcome outcome) {
        var start = Instant.parse("2026-10-13T12:00:00.123456Z");
        Integer status = null;
        if (outcome == Outcome.SUCCESS) { status = 200; }
        else if (outcome == Outcome.HTTP_FAILURE) { status = 503; }
        return new CheckResult(monitorId, executionId, 3, 7, start, start.plusMillis(123), 123456789, outcome, status);
    }

    private long count() { return jdbc.queryForObject("SELECT count(*) FROM monitor.check_results", Long.class); }

    private JdbcTemplate sql() {
        return new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }
}
