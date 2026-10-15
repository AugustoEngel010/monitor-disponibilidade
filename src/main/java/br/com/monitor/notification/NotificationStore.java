package br.com.monitor.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class NotificationStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private static final String SNAPSHOT = "SELECT i.*, t.incident_id, t.type FROM monitor.notification_intents i"
            + " JOIN monitor.state_transitions t ON t.id=i.transition_id";

    public enum Result {
        ACCEPTED("ACCEPTED", null), FAILED("FAILED", "SMTP_FAILURE"),
        UNCERTAIN("UNCERTAIN", "SMTP_UNCERTAIN"), RESTART_UNCERTAIN("UNCERTAIN", "RESTART_UNCERTAIN");

        private final String outcome;
        private final String error;
        Result(String outcome, String error) { this.outcome = outcome; this.error = error; }
    }

    public NotificationStore(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Optional<NotificationDelivery> claim(Instant now) {
        return transaction.execute(status -> {
            var parameters = new MapSqlParameterSource("now", Timestamp.from(now));
            var candidates = jdbc.query(SNAPSHOT + " WHERE i.status='PENDING' AND i.next_attempt_at<=:now"
                    + " AND (t.type='DOWN' OR NOT EXISTS (SELECT 1 FROM monitor.notification_intents d"
                    + " JOIN monitor.state_transitions dt ON dt.id=d.transition_id"
                    + " WHERE dt.incident_id=t.incident_id AND dt.type='DOWN'"
                    + " AND d.status NOT IN ('ACCEPTED','EXHAUSTED')))"
                    + " ORDER BY i.next_attempt_at, i.id LIMIT 1 FOR UPDATE OF i SKIP LOCKED",
                    parameters, NotificationStore::map);
            if (candidates.isEmpty()) { return Optional.empty(); }
            var pending = candidates.getFirst();
            var delivery = new NotificationDelivery(pending.id(), pending.attempt() + 1, pending.incidentId(),
                    pending.type(), pending.recipient(), pending.monitorName(), pending.monitorUrl(),
                    pending.firstFailureAt(), pending.downConfirmedAt(),
                    pending.recoveryStartedAt(), pending.recoveredAt());
            var key = key(delivery).addValue("now", Timestamp.from(now));
            jdbc.update("UPDATE monitor.notification_intents SET status='SENDING',attempt_count=:attempt,"
                    + "next_attempt_at=NULL WHERE id=:id", key);
            jdbc.update("INSERT INTO monitor.notification_attempts"
                    + " (notification_id,attempt_number,started_at,outcome) VALUES (:id,:attempt,:now,'SENDING')", key);
            return Optional.of(delivery);
        });
    }

    public void finish(NotificationDelivery delivery, Result result, Instant now) {
        transaction.executeWithoutResult(status -> complete(delivery, result, now));
    }

    public void recover(Instant now) {
        transaction.executeWithoutResult(status -> {
            var orphaned = jdbc.query(SNAPSHOT + " WHERE i.status='SENDING' ORDER BY i.id FOR UPDATE OF i",
                    new MapSqlParameterSource(), NotificationStore::map);
            for (var delivery : orphaned) { complete(delivery, Result.RESTART_UNCERTAIN, now); }
        });
    }

    private void complete(NotificationDelivery delivery, Result result, Instant now) {
        var parameters = key(delivery).addValue("finished", Timestamp.from(now))
                .addValue("outcome", result.outcome).addValue("error", result.error);
        var rows = jdbc.queryForList("SELECT status,attempt_count FROM monitor.notification_intents"
                + " WHERE id=:id FOR UPDATE", parameters);
        if (rows.isEmpty() || !rows.getFirst().get("status").equals("SENDING")
                || ((Number) rows.getFirst().get("attempt_count")).intValue() != delivery.attempt()) { return; }
        int updated = jdbc.update("UPDATE monitor.notification_attempts SET finished_at=:finished,"
                + "outcome=:outcome,error_code=:error WHERE notification_id=:id AND attempt_number=:attempt"
                + " AND outcome='SENDING'", parameters);
        if (updated != 1) { throw new IllegalStateException("Delivery reservation has no open attempt"); }
        boolean terminal = result == Result.ACCEPTED || delivery.attempt() == 5;
        parameters.addValue("status", result == Result.ACCEPTED ? "ACCEPTED" : terminal ? "EXHAUSTED" : "PENDING")
                .addValue("completed", terminal ? Timestamp.from(now) : null)
                .addValue("next", terminal ? null : Timestamp.from(now.plusSeconds(30L << (delivery.attempt() - 1))));
        jdbc.update("UPDATE monitor.notification_intents SET status=:status,completed_at=:completed,"
                + "next_attempt_at=:next WHERE id=:id", parameters);
    }

    private static MapSqlParameterSource key(NotificationDelivery delivery) {
        return new MapSqlParameterSource("id", delivery.id()).addValue("attempt", delivery.attempt());
    }

    private static NotificationDelivery map(ResultSet row, int index) throws SQLException {
        return new NotificationDelivery(row.getObject("id", java.util.UUID.class), row.getInt("attempt_count"),
                row.getObject("incident_id", java.util.UUID.class), row.getString("type"), row.getString("recipient"),
                row.getString("monitor_name"), row.getString("monitor_url"),
                row.getTimestamp("first_failure_at").toInstant(),
                row.getTimestamp("down_confirmed_at").toInstant(), instant(row.getTimestamp("recovery_started_at")),
                instant(row.getTimestamp("recovered_at")));
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
