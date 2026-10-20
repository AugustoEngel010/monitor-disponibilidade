package br.com.monitor.monitoring;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import br.com.monitor.operation.OperationalEvents;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class CheckRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public CheckRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public CheckResult save(CheckResult result) {
        // Serialize retirement/replay with cleanup and application using the monitor lock.
        jdbc.queryForList("SELECT id FROM monitor.monitors WHERE id=:id FOR UPDATE",
                new MapSqlParameterSource("id", result.monitorId()));
        var parameters = new MapSqlParameterSource("execution_id", result.executionId())
                .addValue("monitor_id", result.monitorId())
                .addValue("configuration_version", result.configurationVersion())
                .addValue("execution_generation", result.executionGeneration())
                .addValue("started_at", Timestamp.from(result.startedAt()))
                .addValue("finished_at", Timestamp.from(result.finishedAt()))
                .addValue("duration_nanos", result.durationNanos()).addValue("outcome", result.outcome().name())
                .addValue("http_status", result.httpStatus()).addValue("error_message", result.errorMessage());
        int inserted = jdbc.update("INSERT INTO monitor.check_results (execution_id, monitor_id, configuration_version,"
                + " execution_generation, started_at, finished_at, duration_nanos, outcome, http_status, error_message)"
                + " VALUES (:execution_id, :monitor_id, :configuration_version, :execution_generation, :started_at,"
                + " :finished_at, :duration_nanos, :outcome, :http_status, :error_message)"
                + " ON CONFLICT DO NOTHING", parameters);
        var saved = find(result.executionId()).orElseThrow();
        if (!saved.monitorId().equals(result.monitorId())
                || saved.configurationVersion() != result.configurationVersion()
                || saved.executionGeneration() != result.executionGeneration()) {
            throw new IllegalArgumentException("Execution identifier belongs to another snapshot");
        }
        if (inserted == 1) {
            OperationalEvents.committed(OperationalEvents.Event.CHECK_RECORDED, saved.monitorId(),
                    saved.executionId(), null, null, saved.outcome());
        }
        return saved;
    }

    public Optional<CheckResult> find(UUID executionId) {
        return jdbc.query("SELECT * FROM monitor.check_results WHERE execution_id=:id",
                new MapSqlParameterSource("id", executionId), this::map).stream().findFirst();
    }

    public boolean retired(UUID executionId) {
        return jdbc.queryForObject("SELECT count(*) FROM monitor.retired_executions WHERE execution_id=:id",
                new MapSqlParameterSource("id", executionId), Integer.class) != 0;
    }

    private CheckResult map(ResultSet row, int index) throws SQLException {
        return new CheckResult(row.getObject("monitor_id", UUID.class), row.getObject("execution_id", UUID.class),
                row.getLong("configuration_version"), row.getLong("execution_generation"),
                row.getTimestamp("started_at").toInstant(), row.getTimestamp("finished_at").toInstant(),
                row.getLong("duration_nanos"), CheckResult.Outcome.valueOf(row.getString("outcome")),
                row.getObject("http_status", Integer.class));
    }
}
