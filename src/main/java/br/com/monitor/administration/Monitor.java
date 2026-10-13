package br.com.monitor.administration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Monitor(UUID id, MonitorConfiguration configuration, State state, Instant createdAt, Instant updatedAt) {
    public record State(String lifecycle, String confirmedState, int failureCount, int recoveryCount,
            Instant firstFailureAt, Instant recoveryStartedAt, Instant lastObservedAt, boolean currentObservation,
            UUID openIncidentId, long configurationVersion, long executionGeneration, Instant nextCheckAt) { }

    public static Monitor create(UUID id, MonitorConfiguration configuration, Instant now) {
        return new Monitor(id, configuration, new State("ACTIVE", "UNKNOWN", 0, 0, null, null, null,
                false, null, 1, 1, now), now, now);
    }

    public Monitor edit(MonitorConfiguration replacement, Instant now) {
        if (configuration.equals(replacement)) { return this; }
        boolean policyChanged = !configuration.samePolicy(replacement);
        if (policyChanged && state.openIncidentId != null) {
            throw new MonitorProblem(409, "OPEN_INCIDENT", Map.of());
        }
        State next = state;
        if (policyChanged) {
            next = reset(state.lifecycle, "UNKNOWN", state.configurationVersion + 1,
                    "ACTIVE".equals(state.lifecycle) ? now : null);
        }
        return new Monitor(id, replacement, next, createdAt, now);
    }

    public Monitor pause(Instant now) {
        if ("PAUSED".equals(state.lifecycle)) { return this; }
        return new Monitor(id, configuration, reset("PAUSED", state.confirmedState,
                state.configurationVersion, null), createdAt, now);
    }

    public Monitor resume(Instant now) {
        if ("ACTIVE".equals(state.lifecycle)) { return this; }
        return new Monitor(id, configuration, reset("ACTIVE", state.confirmedState,
                state.configurationVersion, now), createdAt, now);
    }

    public String evidence(Instant now) {
        if (!state.currentObservation || state.lastObservedAt == null) { return "NEVER_OBSERVED"; }
        var age = Duration.between(state.lastObservedAt, now);
        return !age.isNegative() && age.compareTo(Duration.ofSeconds(2L * configuration.intervalSeconds()
                + configuration.timeoutSeconds())) <= 0 ? "FRESH" : "STALE";
    }

    private State reset(String lifecycle, String confirmed, long version, Instant nextCheck) {
        return new State(lifecycle, confirmed, 0, 0, null, null, state.lastObservedAt, false,
                state.openIncidentId, version, state.executionGeneration + 1, nextCheck);
    }
}
