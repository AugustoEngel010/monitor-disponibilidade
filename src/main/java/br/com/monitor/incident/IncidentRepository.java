package br.com.monitor.incident;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.operation.OperationalEvents;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public IncidentRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Incident(UUID id, UUID monitorId, long configurationVersion, Instant firstFailureAt,
            Instant downConfirmedAt, Instant recoveryStartedAt, Instant recoveredAt) { }

    public Optional<Incident> find(UUID id) {
        return jdbc.query("SELECT * FROM monitor.incidents WHERE id=:id", new MapSqlParameterSource("id", id),
                (row, index) -> new Incident(row.getObject("id", UUID.class), row.getObject("monitor_id", UUID.class),
                        row.getLong("configuration_version"), row.getTimestamp("first_failure_at").toInstant(),
                        row.getTimestamp("down_confirmed_at").toInstant(),
                        instant(row.getTimestamp("recovery_started_at")), instant(row.getTimestamp("recovered_at"))))
                .stream().findFirst();
    }

    public boolean processed(UUID executionId) {
        return jdbc.queryForObject("SELECT count(*) FROM monitor.result_applications WHERE execution_id=:id",
                new MapSqlParameterSource("id", executionId), Integer.class) != 0;
    }

    public Optional<Instant> lastAppliedStart(CheckResult result) {
        return jdbc.query("SELECT max(a.logical_started_at) AS latest FROM monitor.result_applications a"
                + " JOIN monitor.check_results r USING (execution_id) WHERE r.monitor_id=:monitor"
                + " AND r.configuration_version=:version AND r.execution_generation=:generation"
                + " AND a.disposition='APPLIED'", snapshot(result),
                (row, index) -> Optional.ofNullable(instant(row.getTimestamp("latest")))).getFirst();
    }

    public void mark(CheckResult result, Instant logicalStart, boolean applied) {
        jdbc.update("INSERT INTO monitor.result_applications (execution_id, disposition, logical_started_at)"
                + " VALUES (:id, :disposition, :started)", new MapSqlParameterSource("id", result.executionId())
                .addValue("disposition", applied ? "APPLIED" : "HISTORY_ONLY")
                .addValue("started", timestamp(logicalStart)));
    }

    public void open(Monitor monitor, Instant confirmedAt) {
        jdbc.update("INSERT INTO monitor.incidents (id, monitor_id, configuration_version, first_failure_at,"
                + " down_confirmed_at) VALUES (:id, :monitor, :version, :first, :confirmed)",
                new MapSqlParameterSource("id", monitor.state().openIncidentId()).addValue("monitor", monitor.id())
                .addValue("version", monitor.state().configurationVersion())
                .addValue("first", timestamp(monitor.state().firstFailureAt()))
                .addValue("confirmed", timestamp(confirmedAt)));
    }

    public void recoveryStarted(UUID incidentId, Instant startedAt) {
        jdbc.update("UPDATE monitor.incidents SET recovery_started_at=:started WHERE id=:id AND recovered_at IS NULL",
                new MapSqlParameterSource("id", incidentId).addValue("started", timestamp(startedAt)));
    }

    public void close(UUID incidentId, Instant startedAt, Instant confirmedAt) {
        int updated = jdbc.update("UPDATE monitor.incidents SET recovery_started_at=:started, recovered_at=:confirmed"
                + " WHERE id=:id AND recovered_at IS NULL", new MapSqlParameterSource("id", incidentId)
                .addValue("started", timestamp(startedAt)).addValue("confirmed", timestamp(confirmedAt)));
        if (updated != 1) { throw new IllegalStateException("Open incident is missing"); }
    }

    public void transition(Monitor monitor, CheckResult result, UUID incidentId, IncidentStateMachine.Transition type) {
        UUID transitionId = UUID.randomUUID();
        jdbc.update("INSERT INTO monitor.state_transitions (id, execution_id, monitor_id, incident_id,"
                + " configuration_version, type, confirmed_at) VALUES (:id, :execution, :monitor, :incident,"
                + " :version, :type, :confirmed)", snapshot(result).addValue("id", transitionId)
                .addValue("execution", result.executionId()).addValue("incident", incidentId)
                .addValue("type", type.name()).addValue("confirmed", timestamp(result.finishedAt())));
        var incident = find(incidentId).orElseThrow();
        UUID notificationId = UUID.randomUUID();
        jdbc.update("INSERT INTO monitor.notification_intents (id, transition_id, recipient, monitor_name,"
                + " monitor_url, first_failure_at, down_confirmed_at, recovery_started_at, recovered_at)"
                + " VALUES (:id, :transition, :recipient, :name, :url, :first, :down, :started, :recovered)",
                new MapSqlParameterSource("id", notificationId).addValue("transition", transitionId)
                .addValue("recipient", monitor.configuration().alertRecipient())
                .addValue("name", monitor.configuration().name()).addValue("url", monitor.configuration().url())
                .addValue("first", timestamp(incident.firstFailureAt()))
                .addValue("down", timestamp(incident.downConfirmedAt()))
                .addValue("started", timestamp(incident.recoveryStartedAt()))
                .addValue("recovered", timestamp(incident.recoveredAt())));
        OperationalEvents.committed(OperationalEvents.Event.INCIDENT_TRANSITION, monitor.id(), result.executionId(),
                incidentId, notificationId, type);
    }

    private static MapSqlParameterSource snapshot(CheckResult result) {
        return new MapSqlParameterSource("monitor", result.monitorId())
                .addValue("version", result.configurationVersion())
                .addValue("generation", result.executionGeneration());
    }

    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
