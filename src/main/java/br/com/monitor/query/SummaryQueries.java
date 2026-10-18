package br.com.monitor.query;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.monitoring.CheckResult.Outcome;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SummaryQueries {
    private final NamedParameterJdbcTemplate jdbc;
    private final HistoryQueries history;
    private final Clock clock;

    public SummaryQueries(NamedParameterJdbcTemplate jdbc, HistoryQueries history, Clock clock) {
        this.jdbc = jdbc;
        this.history = history;
        this.clock = clock;
    }

    public record Window(Instant from, Instant to, String timeZone) { }
    public record Period(Instant from, Instant to) { }
    public record Statistics(String metricType, String dataStatus, long totalChecks, long validSamples,
            long successSamples, long latencySamples, BigDecimal successRatePercent,
            BigDecimal averageResponseTimeMs) { }
    public record Summary(UUID monitorId, Instant generatedAt, Window window, String lifecycle, String confirmedState,
            String evidence, boolean recentObservation, String label, Instant lastObservedAt,
            HistoryQueries.CheckView lastObservation, int failureCount, int recoveryCount, long configurationVersion,
            long executionGeneration, Instant nextCheckAt, HistoryQueries.IncidentView currentIncident,
            Statistics statistics, Instant coverageKnownFrom, List<Period> noObservationPeriods) { }

    public Summary summary(UUID id, QueryParameters q) {
        var monitor = history.required(id);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var s = monitor.state();
        var parameters = HistoryQueries.parameters(id, q).addValue("external", Arrays.stream(Outcome.values())
                .filter(Outcome::external).map(Enum::name).toList());
        var statistics = jdbc.query("SELECT count(*) AS total, count(*) FILTER (WHERE outcome IN (:external)) AS valid,"
                + " count(*) FILTER (WHERE outcome='SUCCESS') AS successes,"
                + " count(*) FILTER (WHERE outcome='SUCCESS' AND duration_nanos>=0) AS latency_samples,"
                + " avg(duration_nanos) FILTER (WHERE outcome='SUCCESS' AND duration_nanos>=0) AS latency"
                + " FROM monitor.check_results WHERE monitor_id=:id AND finished_at>=:from AND finished_at<:to",
                parameters, (row, index) -> {
                    long valid = row.getLong("valid");
                    long successes = row.getLong("successes");
                    BigDecimal latency = row.getBigDecimal("latency");
                    return new Statistics("CHECK_SUCCESS_SAMPLING", valid == 0 ? "NO_DATA" : "HAS_DATA",
                            row.getLong("total"), valid, successes, row.getLong("latency_samples"), valid == 0 ? null
                                    : BigDecimal.valueOf(successes).multiply(BigDecimal.valueOf(100))
                                            .divide(BigDecimal.valueOf(valid), 2, RoundingMode.HALF_UP),
                            latency == null ? null : latency.divide(BigDecimal.valueOf(1_000_000),
                                    2, RoundingMode.HALF_UP));
                }).getFirst();
        var last = jdbc.query("SELECT r.* FROM monitor.check_results r JOIN monitor.result_applications a"
                + " USING (execution_id) WHERE r.monitor_id=:id AND a.disposition='APPLIED'"
                + " AND r.outcome IN (:external) AND r.finished_at=:observed"
                + " ORDER BY r.finished_at DESC,r.execution_id DESC LIMIT 1",
                parameters.addValue("observed", s.lastObservedAt() == null ? null : Timestamp.from(s.lastObservedAt())),
                HistoryQueries::check).stream().findFirst().orElse(null);
        var known = jdbc.query("SELECT min(started_at) AS known FROM monitor.observation_epochs WHERE monitor_id=:id",
                new MapSqlParameterSource("id", id), (row, index) -> HistoryQueries.instant(row, "known")).getFirst();
        String evidence = monitor.evidence(now);
        return new Summary(id, now, new Window(q.from(), q.to(), "UTC"), s.lifecycle(), s.confirmedState(), evidence,
                "FRESH".equals(evidence), label(monitor, evidence), s.lastObservedAt(), last,
                s.failureCount(), s.recoveryCount(), s.configurationVersion(), s.executionGeneration(), s.nextCheckAt(),
                history.currentIncident(s.openIncidentId(), now), statistics, known, gaps(q, parameters));
    }

    private List<Period> gaps(QueryParameters q, MapSqlParameterSource parameters) {
        // Epoch snapshots preserve the policy in effect at the observation, including old generations.
        var coverage = jdbc.query("SELECT greatest(r.finished_at,:from) AS coverage_start,"
                + " least(r.finished_at+e.freshness_seconds*interval '1 second',coalesce(e.ended_at,:to),:to)"
                + " AS coverage_end"
                + " FROM monitor.check_results r JOIN monitor.result_applications a USING (execution_id)"
                + " JOIN monitor.observation_epochs e ON e.monitor_id=r.monitor_id"
                + " AND e.execution_generation=r.execution_generation"
                + " AND e.configuration_version=r.configuration_version"
                + " WHERE r.monitor_id=:id AND a.disposition='APPLIED' AND r.outcome IN (:external)"
                + " AND e.lifecycle='ACTIVE' AND r.finished_at>=e.started_at"
                + " AND (e.ended_at IS NULL OR r.finished_at<e.ended_at)"
                + " AND r.finished_at<:to AND r.finished_at+e.freshness_seconds*interval '1 second'>:from"
                + " AND (e.ended_at IS NULL OR e.ended_at>:from) ORDER BY coverage_start,coverage_end", parameters,
                (row, index) -> new Period(HistoryQueries.instant(row, "coverage_start"),
                        HistoryQueries.instant(row, "coverage_end")));
        var missing = new ArrayList<Period>();
        Instant cursor = q.from();
        for (var period : coverage) {
            if (period.from().isAfter(cursor)) { missing.add(new Period(cursor, period.from())); }
            if (period.to().isAfter(cursor)) { cursor = period.to(); }
        }
        if (cursor.isBefore(q.to())) { missing.add(new Period(cursor, q.to())); }
        return List.copyOf(missing);
    }

    private String label(Monitor monitor, String evidence) {
        var s = monitor.state();
        if ("PAUSED".equals(s.lifecycle())) { return "PAUSED"; }
        if ("NEVER_OBSERVED".equals(evidence)) { return "SEM_DADOS"; }
        if ("STALE".equals(evidence)) { return "SEM_DADOS_RECENTES"; }
        if (!"DOWN".equals(s.confirmedState()) && s.failureCount() > 0) { return "CONFIRMANDO_QUEDA"; }
        if ("DOWN".equals(s.confirmedState()) && s.recoveryCount() > 0) { return "CONFIRMANDO_RECUPERACAO"; }
        return s.confirmedState();
    }
}
