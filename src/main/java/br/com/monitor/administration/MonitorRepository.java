package br.com.monitor.administration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MonitorRepository {
    private static final String[] COLUMNS = {"id", "name", "url", "interval_seconds", "timeout_seconds",
        "expected_status", "failure_threshold", "recovery_threshold", "alert_recipient", "lifecycle",
        "confirmed_state", "failure_count", "recovery_count", "first_failure_at", "recovery_started_at",
        "last_observed_at", "current_observation", "open_incident_id", "configuration_version",
        "execution_generation", "next_check_at", "created_at", "updated_at"};
    private final NamedParameterJdbcTemplate jdbc;

    public MonitorRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(Monitor monitor) {
        String placeholders = Arrays.stream(COLUMNS).map(column -> ":" + column).collect(Collectors.joining(","));
        jdbc.update("INSERT INTO monitor.monitors (" + String.join(",", COLUMNS) + ") VALUES ("
                + placeholders + ")", parameters(monitor));
    }

    public void save(Monitor monitor) {
        String assignments = Arrays.stream(COLUMNS).filter(column -> !column.equals("id")
                && !column.equals("created_at")).map(column -> column + "=:" + column)
                .collect(Collectors.joining(","));
        jdbc.update("UPDATE monitor.monitors SET " + assignments + " WHERE id=:id", parameters(monitor));
    }

    public Optional<Monitor> find(UUID id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM monitor.monitors WHERE id=:id" + (lock ? " FOR UPDATE" : ""),
                new MapSqlParameterSource("id", id), this::map);
        return rows.stream().findFirst();
    }

    public long count() {
        return jdbc.queryForObject("SELECT count(*) FROM monitor.monitors", new MapSqlParameterSource(), Long.class);
    }

    public List<Monitor> list(int page, int size) {
        return jdbc.query("SELECT * FROM monitor.monitors ORDER BY created_at ASC, id ASC LIMIT :size OFFSET :offset",
                new MapSqlParameterSource("size", size).addValue("offset", (long) page * size), this::map);
    }

    public void lockCreation() {
        jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(704004)");
    }

    private MapSqlParameterSource parameters(Monitor monitor) {
        var c = monitor.configuration();
        var s = monitor.state();
        Object[] values = {monitor.id(), c.name(), c.url(), c.intervalSeconds(), c.timeoutSeconds(),
            c.expectedStatus(), c.failureThreshold(), c.recoveryThreshold(), c.alertRecipient(), s.lifecycle(),
            s.confirmedState(), s.failureCount(), s.recoveryCount(), timestamp(s.firstFailureAt()),
            timestamp(s.recoveryStartedAt()), timestamp(s.lastObservedAt()), s.currentObservation(),
            s.openIncidentId(), s.configurationVersion(), s.executionGeneration(), timestamp(s.nextCheckAt()),
            timestamp(monitor.createdAt()), timestamp(monitor.updatedAt())};
        var parameters = new MapSqlParameterSource();
        for (int i = 0; i < COLUMNS.length; i++) { parameters.addValue(COLUMNS[i], values[i]); }
        return parameters;
    }

    private Monitor map(ResultSet row, int index) throws SQLException {
        var configuration = new MonitorConfiguration(row.getString("name"), row.getString("url"),
                row.getInt("interval_seconds"), row.getInt("timeout_seconds"), row.getInt("expected_status"),
                row.getInt("failure_threshold"), row.getInt("recovery_threshold"), row.getString("alert_recipient"));
        var state = new Monitor.State(row.getString("lifecycle"), row.getString("confirmed_state"),
                row.getInt("failure_count"), row.getInt("recovery_count"), instant(row, "first_failure_at"),
                instant(row, "recovery_started_at"), instant(row, "last_observed_at"),
                row.getBoolean("current_observation"), row.getObject("open_incident_id", UUID.class),
                row.getLong("configuration_version"), row.getLong("execution_generation"),
                instant(row, "next_check_at"));
        return new Monitor(row.getObject("id", UUID.class), configuration, state,
                instant(row, "created_at"), instant(row, "updated_at"));
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
