package br.com.monitor.operation;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RetentionService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final int days;
    private final int batchSize;

    public RetentionService(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager manager,
            Clock clock, Environment environment) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.clock = clock;
        days = setting(environment, "monitor.retention.days", 30, 1, 3650);
        batchSize = setting(environment, "monitor.retention.batch-size", 1000, 1, 10000);
    }

    public record Cleanup(Instant historyAvailableFrom, int incidents, int checks, int epochs, boolean more) { }

    public Cleanup clean() {
        return transaction.execute(status -> {
            jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout='2s'");
            jdbc.getJdbcTemplate().execute("SET LOCAL statement_timeout='5s'");
            Instant proposed = clock.instant().minus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
            Instant previous = jdbc.query("SELECT history_available_from FROM monitor.retention_state"
                    + " WHERE singleton FOR UPDATE", new MapSqlParameterSource(), (row, index) -> {
                        Timestamp time = row.getTimestamp(1);
                        return time == null ? null : time.toInstant();
                    }).getFirst();
            Instant cutoff = previous != null && previous.isAfter(proposed) ? previous : proposed;
            var p = new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)).addValue("batch", batchSize);
            // Same lock/order as administration and result application; no network runs under these locks.
            jdbc.queryForList("SELECT id FROM monitor.monitors ORDER BY id FOR UPDATE", p);
            var incidents = jdbc.query("SELECT i.id FROM monitor.incidents i WHERE i.recovered_at<:cutoff"
                    + " AND NOT EXISTS (SELECT 1 FROM monitor.notification_intents n"
                    + " JOIN monitor.state_transitions t ON t.id=n.transition_id WHERE t.incident_id=i.id"
                    + " AND (n.status IN ('PENDING','SENDING') OR n.completed_at>=:cutoff))"
                    + " ORDER BY i.recovered_at,i.id LIMIT :batch", p,
                    (row, index) -> row.getObject(1, UUID.class));
            if (!incidents.isEmpty()) {
                p.addValue("incidents", incidents);
                String chain = "SELECT t.id FROM monitor.state_transitions t WHERE t.incident_id IN (:incidents)";
                jdbc.update("DELETE FROM monitor.notification_attempts WHERE notification_id IN"
                        + " (SELECT id FROM monitor.notification_intents WHERE transition_id IN (" + chain + "))", p);
                jdbc.update("DELETE FROM monitor.notification_intents WHERE transition_id IN (" + chain + ")", p);
                jdbc.update("DELETE FROM monitor.state_transitions WHERE incident_id IN (:incidents)", p);
                jdbc.update("DELETE FROM monitor.incidents WHERE id IN (:incidents)", p);
            }
            var checks = jdbc.query("SELECT r.execution_id FROM monitor.check_results r"
                    + " JOIN monitor.result_applications a USING (execution_id)"
                    + " JOIN monitor.monitors m ON m.id=r.monitor_id WHERE r.finished_at<:cutoff"
                    + " AND NOT EXISTS (SELECT 1 FROM monitor.check_attempts x WHERE x.execution_id=r.execution_id)"
                    + " AND NOT EXISTS (SELECT 1 FROM monitor.state_transitions t WHERE t.execution_id=r.execution_id)"
                    + " AND r.finished_at IS DISTINCT FROM m.last_observed_at"
                    + " AND NOT (a.disposition='APPLIED' AND r.execution_generation=m.execution_generation"
                    + " AND r.configuration_version=m.configuration_version AND NOT EXISTS"
                    + " (SELECT 1 FROM monitor.result_applications newer JOIN monitor.check_results nr"
                    + " USING (execution_id) WHERE nr.monitor_id=r.monitor_id"
                    + " AND nr.execution_generation=r.execution_generation"
                    + " AND nr.configuration_version=r.configuration_version AND newer.disposition='APPLIED'"
                    + " AND (newer.logical_started_at,nr.execution_id)>(a.logical_started_at,r.execution_id)))"
                    + " ORDER BY r.finished_at,r.execution_id LIMIT :batch", p,
                    (row, index) -> row.getObject(1, UUID.class));
            if (!checks.isEmpty()) {
                p.addValue("checks", checks);
                jdbc.update("INSERT INTO monitor.retired_executions SELECT execution_id,monitor_id"
                        + " FROM monitor.check_results WHERE execution_id IN (:checks)", p);
                jdbc.update("DELETE FROM monitor.result_applications WHERE execution_id IN (:checks)", p);
                jdbc.update("DELETE FROM monitor.check_results WHERE execution_id IN (:checks)", p);
            }
            int epochs = jdbc.update("DELETE FROM monitor.observation_epochs WHERE (monitor_id,execution_generation)"
                    + " IN (SELECT e.monitor_id,e.execution_generation FROM monitor.observation_epochs e"
                    + " WHERE e.ended_at<:cutoff AND NOT EXISTS (SELECT 1 FROM monitor.check_results r"
                    + " WHERE r.monitor_id=e.monitor_id AND r.execution_generation=e.execution_generation)"
                    + " AND NOT EXISTS (SELECT 1 FROM monitor.check_attempts a WHERE a.monitor_id=e.monitor_id"
                    + " AND a.execution_generation=e.execution_generation) ORDER BY e.ended_at LIMIT :batch)", p);
            jdbc.update("UPDATE monitor.retention_state SET history_available_from=:cutoff WHERE singleton", p);
            return new Cleanup(cutoff, incidents.size(), checks.size(), epochs,
                    incidents.size() == batchSize || checks.size() == batchSize || epochs == batchSize);
        });
    }

    static int setting(Environment environment, String name, int fallback, int min, int max) {
        try {
            int value = environment.getProperty(name, Integer.class, fallback);
            if (value < min || value > max) { throw new IllegalArgumentException(); }
            return value;
        } catch (RuntimeException error) { throw new IllegalArgumentException("Invalid retention configuration"); }
    }
}
