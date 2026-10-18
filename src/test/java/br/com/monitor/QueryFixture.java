package br.com.monitor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.incident.IncidentProcessor;
import br.com.monitor.incident.IncidentRepository;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.query.HistoryQueries;
import br.com.monitor.query.QueryParameters;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class QueryFixture {
    final Instant base = Instant.parse("2026-10-18T12:00:00Z");
    final Clock clock = Clock.fixed(base.plusSeconds(600), ZoneOffset.UTC);
    final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    JdbcTemplate jdbc;
    NamedParameterJdbcTemplate named;
    MonitorRepository monitors;
    CheckRepository checks;
    IncidentRepository incidents;
    IncidentProcessor processor;
    HistoryQueries history;
    Monitor monitor;

    @BeforeAll
    void startDatabase() {
        db.start();
        var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        named = new NamedParameterJdbcTemplate(source);
        monitors = new MonitorRepository(named);
        checks = new CheckRepository(named);
        incidents = new IncidentRepository(named);
        processor = new IncidentProcessor(monitors, checks, incidents, named,
                new DataSourceTransactionManager(source));
        history = new HistoryQueries(named, monitors, clock);
    }

    @BeforeEach
    void clearDatabase() {
        jdbc.execute("TRUNCATE monitor.monitors CASCADE");
        monitor = newMonitor();
    }

    @AfterAll
    void stopDatabase() { db.stop(); }

    Monitor newMonitor() {
        var value = Monitor.create(UUID.randomUUID(), MonitorConfiguration.validated("query",
                "https://example.org", 60, 5, 200, 1, 1, "ops@example.org"), base.minusSeconds(1000));
        monitors.insert(value);
        return value;
    }

    CheckResult check(Monitor target, int id, long second, CheckResult.Outcome outcome, long nanos, boolean applied) {
        var state = monitors.find(target.id(), false).orElseThrow().state();
        var time = base.plusSeconds(second);
        var result = checks.save(new CheckResult(target.id(), uuid(id), state.configurationVersion(),
                state.executionGeneration(), time, time, nanos, outcome,
                outcome == CheckResult.Outcome.SUCCESS ? 200
                        : outcome == CheckResult.Outcome.HTTP_FAILURE ? 503 : null));
        if (applied) {
            jdbc.update("INSERT INTO monitor.result_applications VALUES (?,'APPLIED',?)",
                    result.executionId(), Timestamp.from(time));
        }
        return result;
    }

    UUID uuid(int value) { return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value)); }

    QueryParameters window(long from, long to, int page, int size, String outcome, String status, String type) {
        return new QueryParameters(base.plusSeconds(from), base.plusSeconds(to), page, size, outcome, status, type);
    }
}
