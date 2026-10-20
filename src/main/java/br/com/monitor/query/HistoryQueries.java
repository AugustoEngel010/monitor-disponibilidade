package br.com.monitor.query;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorProblem;
import br.com.monitor.administration.MonitorRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class HistoryQueries {
    private final NamedParameterJdbcTemplate jdbc;
    private final MonitorRepository monitors;
    private final Clock clock;

    public HistoryQueries(NamedParameterJdbcTemplate jdbc, MonitorRepository monitors, Clock clock) {
        this.jdbc = jdbc;
        this.monitors = monitors;
        this.clock = clock;
    }

    public record Page<T>(List<T> items, int page, int size, long totalElements, long totalPages,
            Instant historyAvailableFrom, List<SummaryQueries.Period> retainedOutPeriods) { }
    public record CheckView(UUID monitorId, UUID executionId, long configurationVersion, long executionGeneration,
            Instant startedAt, Instant finishedAt, long durationNanos, String outcome,
            Integer httpStatus, String errorMessage) { }
    public record IncidentView(UUID id, UUID monitorId, long configurationVersion, String status,
            Instant firstFailureAt, Instant downConfirmedAt, Instant recoveryStartedAt, Instant recoveredAt,
            BigDecimal estimatedDurationSeconds, BigDecimal ageSeconds) { }
    public record AttemptView(int attemptNumber, Instant startedAt, Instant finishedAt, String outcome,
            String errorCode) { }
    public record NotificationView(UUID id, UUID monitorId, UUID transitionId, UUID incidentId,
            String type, Instant confirmedAt, String recipient, String status, int attemptCount,
            Instant nextAttemptAt, Instant completedAt, List<AttemptView> attempts) { }

    public Page<CheckView> checks(UUID id, QueryParameters q) {
        required(id);
        String where = " FROM monitor.check_results r WHERE r.monitor_id=:id"
                + " AND r.finished_at>=:from AND r.finished_at<:to"
                + " AND r.finished_at>=coalesce((SELECT history_available_from FROM monitor.retention_state),"
                + " r.finished_at)"
                + (q.outcome() == null ? "" : " AND r.outcome=:outcome");
        return page("r.*", where, "r.finished_at DESC, r.execution_id DESC", q, parameters(id, q),
                HistoryQueries::check);
    }

    public Page<IncidentView> incidents(UUID id, QueryParameters q) {
        required(id);
        Instant now = clock.instant();
        String where = " FROM monitor.incidents i WHERE i.monitor_id=:id"
                + " AND i.down_confirmed_at>=:from AND i.down_confirmed_at<:to"
                + (q.status() == null ? "" : "OPEN".equals(q.status())
                        ? " AND i.recovered_at IS NULL" : " AND i.recovered_at IS NOT NULL");
        return page("i.*", where, "i.down_confirmed_at DESC, i.id DESC", q, parameters(id, q),
                (row, index) -> incident(row, now));
    }

    public Page<NotificationView> notifications(UUID id, QueryParameters q) {
        required(id);
        String where = " FROM monitor.notification_intents n JOIN monitor.state_transitions t ON t.id=n.transition_id"
                + " WHERE t.monitor_id=:id AND t.confirmed_at>=:from AND t.confirmed_at<:to"
                + (q.status() == null ? "" : " AND n.status=:status")
                + (q.type() == null ? "" : " AND t.type=:type");
        var result = page("n.*, t.monitor_id, t.incident_id, t.type, t.confirmed_at", where,
                "t.confirmed_at DESC, n.id DESC", q, parameters(id, q), (row, index) -> notification(row, List.of()));
        if (result.items().isEmpty()) { return result; }
        Map<UUID, List<AttemptView>> attempts = new HashMap<>();
        jdbc.query("SELECT * FROM monitor.notification_attempts WHERE notification_id IN (:ids)"
                + " ORDER BY notification_id, attempt_number", new MapSqlParameterSource("ids",
                        result.items().stream().map(NotificationView::id).toList()), (row, index) -> {
                            attempts.computeIfAbsent(row.getObject("notification_id", UUID.class),
                                    key -> new ArrayList<>()).add(new AttemptView(row.getInt("attempt_number"),
                                            instant(row, "started_at"), instant(row, "finished_at"),
                                            row.getString("outcome"), row.getString("error_code")));
                            return 0;
                        });
        return new Page<>(result.items().stream().map(n -> new NotificationView(n.id(), n.monitorId(),
                n.transitionId(), n.incidentId(), n.type(), n.confirmedAt(), n.recipient(), n.status(),
                n.attemptCount(), n.nextAttemptAt(), n.completedAt(),
                List.copyOf(attempts.getOrDefault(n.id(), List.of())))).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(),
                result.historyAvailableFrom(), result.retainedOutPeriods());
    }

    Monitor required(UUID id) {
        return monitors.find(id, false)
                .orElseThrow(() -> new MonitorProblem(404, "MONITOR_NOT_FOUND", Map.of()));
    }

    IncidentView currentIncident(UUID id, Instant now) {
        if (id == null) { return null; }
        return jdbc.query("SELECT * FROM monitor.incidents WHERE id=:id", new MapSqlParameterSource("id", id),
                (row, index) -> incident(row, now)).stream().findFirst().orElse(null);
    }

    private <T> Page<T> page(String columns, String where, String order, QueryParameters q,
            MapSqlParameterSource parameters, RowMapper<T> mapper) {
        long count = jdbc.queryForObject("SELECT count(*)" + where, parameters, Long.class);
        var items = jdbc.query("SELECT " + columns + where + " ORDER BY " + order + " LIMIT :size OFFSET :offset",
                parameters, mapper);
        Instant cutoff = historyAvailableFrom();
        return new Page<>(items, q.page(), q.size(), count, (count + q.size() - 1) / q.size(),
                cutoff, retainedOutPeriods(q, cutoff));
    }

    Instant historyAvailableFrom() {
        return jdbc.query("SELECT history_available_from FROM monitor.retention_state",
                new MapSqlParameterSource(), (row, index) -> instant(row, "history_available_from")).getFirst();
    }

    static List<SummaryQueries.Period> retainedOutPeriods(QueryParameters q, Instant cutoff) {
        if (cutoff == null || !q.from().isBefore(cutoff)) { return List.of(); }
        return List.of(new SummaryQueries.Period(q.from(), cutoff.isBefore(q.to()) ? cutoff : q.to()));
    }

    static MapSqlParameterSource parameters(UUID id, QueryParameters q) {
        return new MapSqlParameterSource("id", id).addValue("from", Timestamp.from(q.from()))
                .addValue("to", Timestamp.from(q.to())).addValue("size", q.size()).addValue("offset", q.offset())
                .addValue("outcome", q.outcome()).addValue("status", q.status()).addValue("type", q.type());
    }

    static CheckView check(ResultSet row, int index) throws SQLException {
        return new CheckView(row.getObject("monitor_id", UUID.class), row.getObject("execution_id", UUID.class),
                row.getLong("configuration_version"), row.getLong("execution_generation"), instant(row, "started_at"),
                instant(row, "finished_at"), row.getLong("duration_nanos"), row.getString("outcome"),
                row.getObject("http_status", Integer.class), row.getString("error_message"));
    }

    private static IncidentView incident(ResultSet row, Instant now) throws SQLException {
        Instant first = instant(row, "first_failure_at");
        Instant start = instant(row, "recovery_started_at");
        Instant recovered = instant(row, "recovered_at");
        boolean open = recovered == null;
        return new IncidentView(row.getObject("id", UUID.class), row.getObject("monitor_id", UUID.class),
                row.getLong("configuration_version"), open ? "OPEN" : "CLOSED", first,
                instant(row, "down_confirmed_at"), start, recovered,
                open ? null : seconds(first, start), open ? seconds(first, now) : null);
    }

    private static NotificationView notification(ResultSet row, List<AttemptView> attempts) throws SQLException {
        return new NotificationView(row.getObject("id", UUID.class), row.getObject("monitor_id", UUID.class),
                row.getObject("transition_id", UUID.class), row.getObject("incident_id", UUID.class),
                row.getString("type"), instant(row, "confirmed_at"), row.getString("recipient"),
                row.getString("status"),
                row.getInt("attempt_count"), instant(row, "next_attempt_at"), instant(row, "completed_at"), attempts);
    }

    static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    static BigDecimal seconds(Instant from, Instant to) {
        Duration duration = Duration.between(from, to);
        return BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9))
                .stripTrailingZeros();
    }
}
