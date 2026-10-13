package br.com.monitor;

import java.time.Instant;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorProblem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitorTest {
    private final Instant now = Instant.parse("2026-10-13T12:00:00Z");
    private final MonitorConfiguration config = configuration("n", "https://example.org", 60, 5, 200, 3, 2, "a@e.org");

    @Test
    void creationDoesNotClaimHealthBeforeObservation() {
        var monitor = Monitor.create(UUID.randomUUID(), config, now);
        assertThat(monitor.state()).isEqualTo(new Monitor.State("ACTIVE", "UNKNOWN", 0, 0,
                null, null, null, false, null, 1, 1, now));
        assertThat(monitor.evidence(now)).isEqualTo("NEVER_OBSERVED");
        assertThat(monitor.createdAt()).isEqualTo(now);
        assertThat(monitor.updatedAt()).isEqualTo(now);
    }

    @Test
    void nameAndRecipientPreserveEveryMonitoringFieldEvenWithIncident() {
        var monitor = observed("DOWN", UUID.randomUUID());
        var changed = monitor.edit(configuration("new", config.url(), 60, 5, 200, 3, 2, "new@e.org"), now);
        assertThat(changed.state()).isEqualTo(monitor.state());
        assertThat(changed.id()).isEqualTo(monitor.id());
        assertThat(changed.createdAt()).isEqualTo(monitor.createdAt());
        assertThat(changed.configuration().name()).isEqualTo("new");
        assertThat(changed.configuration().alertRecipient()).isEqualTo("new@e.org");
        assertThat(changed.updatedAt()).isEqualTo(now);
        assertThat(changed.edit(changed.configuration(), now.plusSeconds(10))).isEqualTo(changed);
    }

    @Test
    void eachPolicyFieldIsGuardedByAnOpenIncidentWithoutPartialChanges() {
        var monitor = observed("DOWN", UUID.randomUUID());
        for (var policy : changedPolicies()) {
            assertThatThrownBy(() -> monitor.edit(policy, now)).isInstanceOfSatisfying(MonitorProblem.class, error -> {
                assertThat(error.status()).isEqualTo(409);
                assertThat(error.code()).isEqualTo("OPEN_INCIDENT");
            });
        }
        assertThat(monitor.configuration()).isEqualTo(config);
        assertThat(monitor.state().confirmedState()).isEqualTo("DOWN");
        assertThat(monitor.state().recoveryCount()).isEqualTo(1);
    }

    @Test
    void policyChangeResetsCurrentEvidenceAndSequencesButKeepsIdentityAndLastObservation() {
        var monitor = observed("UP", null);
        for (var policy : changedPolicies()) {
            var changed = monitor.edit(policy, now);
            assertThat(changed.id()).isEqualTo(monitor.id());
            assertThat(changed.createdAt()).isEqualTo(monitor.createdAt());
            assertThat(changed.configuration()).isEqualTo(policy);
            assertThat(changed.state()).isEqualTo(new Monitor.State("ACTIVE", "UNKNOWN", 0, 0,
                    null, null, monitor.state().lastObservedAt(), false, null, 8, 12, now));
            assertThat(changed.evidence(now)).isEqualTo("NEVER_OBSERVED");
        }
        var paused = monitor.pause(now);
        var changed = paused.edit(changedPolicies()[0], now.plusSeconds(1));
        assertThat(changed.state().lifecycle()).isEqualTo("PAUSED");
        assertThat(changed.state().nextCheckAt()).isNull();
    }

    @Test
    void pauseAndResumeAreIdempotentAndNeverRecoverAnIncident() {
        var monitor = observed("DOWN", UUID.randomUUID());
        var paused = monitor.pause(now);
        assertThat(paused.state()).isEqualTo(new Monitor.State("PAUSED", "DOWN", 0, 0, null, null,
                monitor.state().lastObservedAt(), false, monitor.state().openIncidentId(), 7, 12, null));
        assertThat(paused.pause(now.plusSeconds(1))).isEqualTo(paused);
        assertThat(paused.evidence(now)).isEqualTo("NEVER_OBSERVED");
        var resumed = paused.resume(now.plusSeconds(2));
        assertThat(resumed.state()).isEqualTo(new Monitor.State("ACTIVE", "DOWN", 0, 0, null, null,
                monitor.state().lastObservedAt(), false, monitor.state().openIncidentId(), 7, 13, now.plusSeconds(2)));
        assertThat(resumed.resume(now.plusSeconds(3))).isEqualTo(resumed);
        assertThat(resumed.evidence(now.plusSeconds(2))).isEqualTo("NEVER_OBSERVED");
        assertThat(monitor.resume(now)).isEqualTo(monitor);
        assertThat(Monitor.create(UUID.randomUUID(), config, now).pause(now).state().confirmedState())
                .isEqualTo("UNKNOWN");
    }

    @Test
    void freshnessUsesInclusiveBoundaryAndNeverTreatsFutureOrInvalidEvidenceAsFresh() {
        var monitor = observed("UP", null);
        var observedAt = monitor.state().lastObservedAt();
        assertThat(monitor.evidence(observedAt)).isEqualTo("FRESH");
        assertThat(monitor.evidence(observedAt.plusSeconds(125))).isEqualTo("FRESH");
        assertThat(monitor.evidence(observedAt.plusSeconds(125).plusNanos(1))).isEqualTo("STALE");
        assertThat(monitor.evidence(observedAt.minusNanos(1))).isEqualTo("STALE");
        assertThat(monitor.pause(now).evidence(observedAt)).isEqualTo("NEVER_OBSERVED");
    }

    private Monitor observed(String status, UUID incident) {
        return new Monitor(UUID.randomUUID(), config, new Monitor.State("ACTIVE", status, 2, 1,
                now.minusSeconds(90), now.minusSeconds(5), now.minusSeconds(5), true, incident, 7, 11,
                now.plusSeconds(55)), now.minusSeconds(100), now.minusSeconds(5));
    }

    private MonitorConfiguration[] changedPolicies() {
        return new MonitorConfiguration[] {
            configuration("mixed", "https://other.org", 60, 5, 200, 3, 2, "a@e.org"),
            configuration("n", config.url(), 61, 5, 200, 3, 2, "a@e.org"),
            configuration("n", config.url(), 60, 6, 200, 3, 2, "a@e.org"),
            configuration("n", config.url(), 60, 5, 201, 3, 2, "a@e.org"),
            configuration("n", config.url(), 60, 5, 200, 4, 2, "a@e.org"),
            configuration("n", config.url(), 60, 5, 200, 3, 3, "a@e.org")
        };
    }

    private MonitorConfiguration configuration(String name, String url, int i, int t, int s, int f, int r,
            String email) {
        return MonitorConfiguration.validated(name, url, i, t, s, f, r, email);
    }
}
