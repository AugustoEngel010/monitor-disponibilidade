package br.com.monitor.incident;

import java.time.Instant;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.monitoring.CheckResult;

public final class IncidentStateMachine {
    private IncidentStateMachine() { }

    public enum Transition { NONE, DOWN, RECOVERY }
    public record Decision(Monitor monitor, Transition transition) { }

    public static Decision apply(Monitor monitor, CheckResult result, Instant logicalStart, UUID newIncidentId) {
        var before = monitor.state();
        var config = monitor.configuration();
        int failures = before.failureCount();
        int recoveries = before.recoveryCount();
        Instant firstFailure = before.firstFailureAt();
        Instant recoveryStart = before.recoveryStartedAt();
        String confirmed = before.confirmedState();
        UUID incident = before.openIncidentId();
        Instant observed = before.lastObservedAt();
        boolean current = before.currentObservation();
        var transition = Transition.NONE;
        boolean gap = current && observed != null && logicalStart.isAfter(observed.plusSeconds(
                2L * config.intervalSeconds() + config.timeoutSeconds()));
        if (gap || !result.outcome().external()) {
            failures = 0;
            recoveries = 0;
            firstFailure = null;
            recoveryStart = null;
        }
        if (result.outcome().external()) {
            observed = result.finishedAt();
            current = true;
            if (result.outcome() == CheckResult.Outcome.SUCCESS) {
                failures = 0;
                firstFailure = null;
                if ("DOWN".equals(confirmed)) {
                    if (recoveries == 0) { recoveryStart = observed; }
                    recoveries = Math.min(recoveries + 1, config.recoveryThreshold());
                    if (recoveries == config.recoveryThreshold()) {
                        confirmed = "UP";
                        incident = null;
                        transition = Transition.RECOVERY;
                    }
                } else {
                    confirmed = "UP";
                    recoveries = 0;
                    recoveryStart = null;
                }
            } else {
                recoveries = 0;
                recoveryStart = null;
                if (!"DOWN".equals(confirmed)) {
                    if (failures == 0) { firstFailure = observed; }
                    failures = Math.min(failures + 1, config.failureThreshold());
                    if (failures == config.failureThreshold()) {
                        confirmed = "DOWN";
                        incident = newIncidentId;
                        transition = Transition.DOWN;
                    }
                } else { failures = config.failureThreshold(); }
            }
        }
        var state = new Monitor.State(before.lifecycle(), confirmed, failures, recoveries, firstFailure,
                recoveryStart, observed, current, incident, before.configurationVersion(),
                before.executionGeneration(), before.nextCheckAt());
        return new Decision(new Monitor(monitor.id(), config, state, monitor.createdAt(), result.finishedAt()),
                transition);
    }
}
