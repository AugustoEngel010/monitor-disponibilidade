package br.com.monitor.monitoring;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.incident.IncidentProcessor;
import br.com.monitor.incident.IncidentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ScheduleStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final MonitorRepository monitors;
    private final CheckRepository results;
    private final TransactionTemplate transaction;
    private final IncidentProcessor processor;

    public ScheduleStore(NamedParameterJdbcTemplate jdbc, MonitorRepository monitors, CheckRepository results,
            PlatformTransactionManager manager) {
        this(jdbc, monitors, results, manager,
                new IncidentProcessor(monitors, results, new IncidentRepository(jdbc), jdbc, manager));
    }

    @Autowired
    public ScheduleStore(NamedParameterJdbcTemplate jdbc, MonitorRepository monitors, CheckRepository results,
            PlatformTransactionManager manager, IncidentProcessor processor) {
        this.jdbc = jdbc;
        this.monitors = monitors;
        this.results = results;
        this.transaction = new TransactionTemplate(manager);
        this.processor = processor;
    }

    public record Attempt(Monitor monitor, UUID executionId, Instant scheduledAt, Instant startedAt) { }

    public Optional<Attempt> claim(Instant now) {
        return claim(now, List.of());
    }

    public Optional<Attempt> claim(Instant now, Collection<UUID> running) {
        return transaction.execute(status -> {
            var due = monitors.due(now, running);
            if (due.isEmpty()) { return Optional.empty(); }
            var monitor = due.getFirst();
            var attempt = new Attempt(monitor, UUID.randomUUID(), monitor.state().nextCheckAt(), now);
            jdbc.update("INSERT INTO monitor.check_attempts (monitor_id, execution_id, configuration_version,"
                    + " execution_generation, scheduled_at, started_at) VALUES (:monitor, :id, :version,"
                    + " :generation, :scheduled, :started)", new MapSqlParameterSource("monitor", monitor.id())
                    .addValue("id", attempt.executionId()).addValue("version", monitor.state().configurationVersion())
                    .addValue("generation", monitor.state().executionGeneration())
                    .addValue("scheduled", Timestamp.from(attempt.scheduledAt()))
                    .addValue("started", Timestamp.from(now)));
            monitors.schedule(monitor.id(), now.plusSeconds(monitor.configuration().intervalSeconds()));
            return Optional.of(attempt);
        });
    }

    public List<UUID> pending() {
        // Product limit is 100 monitors; never fetch a backlog of executions.
        return jdbc.query("SELECT monitor_id FROM monitor.check_attempts ORDER BY monitor_id",
                new MapSqlParameterSource(), (row, index) -> row.getObject(1, UUID.class));
    }

    public boolean current(Attempt attempt) {
        return monitors.find(attempt.monitor().id(), false).map(monitor ->
                "ACTIVE".equals(monitor.state().lifecycle())
                && monitor.state().configurationVersion() == attempt.monitor().state().configurationVersion()
                && monitor.state().executionGeneration() == attempt.monitor().state().executionGeneration())
                .orElse(false);
    }

    public void finish(Attempt attempt, CheckResult result) {
        if (!attempt.executionId().equals(result.executionId())
                || !attempt.monitor().id().equals(result.monitorId())
                || attempt.monitor().state().configurationVersion() != result.configurationVersion()
                || attempt.monitor().state().executionGeneration() != result.executionGeneration()) {
            throw new IllegalArgumentException("Result does not belong to the reserved snapshot");
        }
        transaction.executeWithoutResult(status -> {
            monitors.find(attempt.monitor().id(), true).orElseThrow();
            results.save(result);
            processor.process(result.executionId());
            remove(attempt.monitor().id(), attempt.executionId());
        });
    }

    public void recover(UUID monitorId, Instant now) {
        transaction.executeWithoutResult(status -> {
            monitors.find(monitorId, true).orElseThrow();
            var parameters = new MapSqlParameterSource("monitor", monitorId);
            var rows = jdbc.queryForList("SELECT * FROM monitor.check_attempts WHERE monitor_id=:monitor", parameters);
            if (rows.isEmpty()) { return; }
            var row = rows.getFirst();
            var id = (UUID) row.get("execution_id");
            long version = ((Number) row.get("configuration_version")).longValue();
            long generation = ((Number) row.get("execution_generation")).longValue();
            if (results.find(id).isEmpty()) {
                results.save(new CheckResult(monitorId, id, version, generation,
                        ((Timestamp) row.get("started_at")).toInstant(), now, 0, CheckResult.Outcome.CANCELLED, null));
            }
            processor.process(id);
            remove(monitorId, id);
        });
    }

    private void remove(UUID monitorId, UUID executionId) {
        jdbc.update("DELETE FROM monitor.check_attempts WHERE monitor_id=:monitor AND execution_id=:id",
                new MapSqlParameterSource("monitor", monitorId).addValue("id", executionId));
    }
}
