package br.com.monitor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.incident.IncidentProcessor;
import br.com.monitor.incident.IncidentRepository;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class NotificationFixture {
    static final Instant BASE = Instant.parse("2026-10-15T12:00:00Z");
    final JdbcTemplate jdbc;
    final NamedParameterJdbcTemplate named;
    final PlatformTransactionManager manager;
    final MonitorRepository monitors;
    final CheckRepository checks;
    final IncidentProcessor processor;

    NotificationFixture(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.manager = manager;
        named = new NamedParameterJdbcTemplate(jdbc);
        monitors = new MonitorRepository(named);
        checks = new CheckRepository(named);
        processor = new IncidentProcessor(monitors, checks, new IncidentRepository(named), named, manager);
    }

    Monitor monitor() {
        var monitor = Monitor.create(UUID.randomUUID(), MonitorConfiguration.validated("Original monitor",
                "https://example.org", 60, 5, 200, 1, 1, "ops@example.org"), BASE);
        monitors.insert(monitor);
        return monitor;
    }

    CheckResult apply(Monitor monitor, CheckResult.Outcome outcome, int seconds) {
        var at = BASE.plusSeconds(seconds);
        var result = checks.save(new CheckResult(monitor.id(), UUID.randomUUID(), 1, 1, at, at, 0,
                outcome, outcome == CheckResult.Outcome.SUCCESS ? 200 : 503));
        processor.process(result.executionId());
        jdbc.update("UPDATE monitor.notification_intents SET next_attempt_at=? WHERE attempt_count=0",
                Timestamp.from(BASE));
        return result;
    }

    UUID down(Monitor monitor) {
        apply(monitor, CheckResult.Outcome.HTTP_FAILURE, 0);
        return jdbc.queryForObject("SELECT i.id FROM monitor.notification_intents i"
                + " JOIN monitor.state_transitions t ON t.id=i.transition_id"
                + " WHERE t.monitor_id=? AND t.type='DOWN'", UUID.class, monitor.id());
    }
}
