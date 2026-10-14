package br.com.monitor.incident;

import java.time.Instant;
import java.util.UUID;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.monitoring.CheckRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IncidentProcessor {
    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final IncidentRepository incidents;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public IncidentProcessor(MonitorRepository monitors, CheckRepository checks, IncidentRepository incidents,
            NamedParameterJdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.monitors = monitors;
        this.checks = checks;
        this.incidents = incidents;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
    }

    public boolean process(UUID executionId) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            var result = checks.find(executionId).orElseThrow();
            var monitor = monitors.find(result.monitorId(), true).orElseThrow();
            if (incidents.processed(executionId)) { return false; }
            var starts = jdbc.query("SELECT started_at FROM monitor.check_attempts WHERE execution_id=:id",
                    new MapSqlParameterSource("id", executionId),
                    (row, index) -> row.getTimestamp("started_at").toInstant());
            Instant logicalStart = starts.isEmpty() ? result.startedAt() : starts.getFirst();
            boolean eligible = "ACTIVE".equals(monitor.state().lifecycle())
                    && monitor.state().configurationVersion() == result.configurationVersion()
                    && monitor.state().executionGeneration() == result.executionGeneration()
                    && incidents.lastAppliedStart(result).map(last -> !logicalStart.isBefore(last)).orElse(true);
            if (!eligible) { incidents.mark(result, logicalStart, false); return false; }
            var decision = IncidentStateMachine.apply(monitor, result, logicalStart, UUID.randomUUID());
            var after = decision.monitor();
            monitors.save(after);
            UUID incidentId = monitor.state().openIncidentId();
            if (decision.transition() == IncidentStateMachine.Transition.DOWN) {
                incidentId = after.state().openIncidentId();
                incidents.open(after, result.finishedAt());
            } else if (decision.transition() == IncidentStateMachine.Transition.RECOVERY) {
                incidents.close(incidentId, after.state().recoveryStartedAt(), result.finishedAt());
            } else if (incidentId != null) {
                incidents.recoveryStarted(incidentId, after.state().recoveryStartedAt());
            }
            if (decision.transition() != IncidentStateMachine.Transition.NONE) {
                incidents.transition(after, result, incidentId, decision.transition());
            }
            incidents.mark(result, logicalStart, true);
            return true;
        }));
    }
}
