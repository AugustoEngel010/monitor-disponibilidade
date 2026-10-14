package br.com.monitor;

import java.time.Instant;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.incident.IncidentStateMachine;
import br.com.monitor.monitoring.CheckResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentStateMachineTest {
    private final Instant base = Instant.parse("2026-10-14T12:00:00Z");
    private final UUID incident = UUID.randomUUID();

    @Test
    void threeFailuresConfirmDownAtThirdObservationAndRepeatedFailuresKeepIdentity() {
        // INC-01/03/08: MON-01 C-02.
        var monitor = initial(3, 2);
        monitor = apply(monitor, Outcome.HTTP_FAILURE, 0);
        assertThat(monitor.state().confirmedState()).isEqualTo("UNKNOWN");
        assertThat(monitor.state().failureCount()).isEqualTo(1);
        assertThat(monitor.state().firstFailureAt()).isEqualTo(base);
        assertThat(monitor.state().openIncidentId()).isNull();
        monitor = apply(monitor, Outcome.TIMEOUT, 60);
        assertThat(monitor.state().failureCount()).isEqualTo(2);
        assertThat(monitor.state().openIncidentId()).isNull();
        var decision = decide(monitor, Outcome.DNS_FAILURE, 120, 120);
        assertThat(decision.transition()).isEqualTo(IncidentStateMachine.Transition.DOWN);
        monitor = decision.monitor();
        assertThat(monitor.state().confirmedState()).isEqualTo("DOWN");
        assertThat(monitor.state().failureCount()).isEqualTo(3);
        assertThat(monitor.state().firstFailureAt()).isEqualTo(base);
        assertThat(monitor.state().lastObservedAt()).isEqualTo(base.plusSeconds(120));
        assertThat(monitor.state().openIncidentId()).isEqualTo(incident);
        for (int i = 3; i < 20; i++) {
            var repeated = decide(monitor, Outcome.HTTP_FAILURE, i * 60, i * 60);
            assertThat(repeated.transition()).isEqualTo(IncidentStateMachine.Transition.NONE);
            monitor = repeated.monitor();
            assertThat(monitor.state().failureCount()).isEqualTo(3);
            assertThat(monitor.state().openIncidentId()).isEqualTo(incident);
            assertThat(monitor.state().firstFailureAt()).isEqualTo(base);
        }
    }

    @Test
    void successInterruptsFailureSequenceAndInitialSuccessHasNoRecovery() {
        // INC-02: C-01/C-03.
        var monitor = apply(initial(3, 2), Outcome.SUCCESS, 0);
        assertThat(monitor.state().confirmedState()).isEqualTo("UP");
        monitor = apply(monitor, Outcome.HTTP_FAILURE, 60);
        monitor = apply(monitor, Outcome.HTTP_FAILURE, 120);
        var success = decide(monitor, Outcome.SUCCESS, 180, 180);
        assertThat(success.transition()).isEqualTo(IncidentStateMachine.Transition.NONE);
        monitor = success.monitor();
        assertThat(monitor.state().failureCount()).isZero();
        assertThat(monitor.state().firstFailureAt()).isNull();
        assertThat(monitor.state().openIncidentId()).isNull();
        monitor = apply(monitor, Outcome.HTTP_FAILURE, 240);
        assertThat(monitor.state().failureCount()).isEqualTo(1);
        assertThat(monitor.state().firstFailureAt()).isEqualTo(base.plusSeconds(240));
    }

    @Test
    void twoSuccessesRecoverAndFailureBetweenThemRestartsRecovery() {
        // INC-04/08: C-04/C-17.
        var monitor = down();
        monitor = apply(monitor, Outcome.SUCCESS, 180);
        assertThat(monitor.state().confirmedState()).isEqualTo("DOWN");
        assertThat(monitor.state().recoveryCount()).isEqualTo(1);
        assertThat(monitor.state().recoveryStartedAt()).isEqualTo(base.plusSeconds(180));
        monitor = apply(monitor, Outcome.CONNECTION_FAILURE, 240);
        assertThat(monitor.state().recoveryCount()).isZero();
        assertThat(monitor.state().recoveryStartedAt()).isNull();
        assertThat(monitor.state().openIncidentId()).isEqualTo(incident);
        monitor = apply(monitor, Outcome.SUCCESS, 300);
        var recovered = decide(monitor, Outcome.SUCCESS, 360, 360);
        assertThat(recovered.transition()).isEqualTo(IncidentStateMachine.Transition.RECOVERY);
        assertThat(recovered.monitor().state().confirmedState()).isEqualTo("UP");
        assertThat(recovered.monitor().state().openIncidentId()).isNull();
        assertThat(recovered.monitor().state().recoveryCount()).isEqualTo(2);
        assertThat(recovered.monitor().state().recoveryStartedAt()).isEqualTo(base.plusSeconds(300));
        assertThat(recovered.monitor().state().lastObservedAt()).isEqualTo(base.plusSeconds(360));
        assertThat(recovered.monitor().state().failureCount()).isZero();
        assertThat(recovered.monitor().state().firstFailureAt()).isNull();
        assertThat(apply(recovered.monitor(), Outcome.SUCCESS, 420).state().recoveryCount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"INTERNAL_ERROR", "DESTINATION_BLOCKED", "CANCELLED"})
    void nonExternalResultInterruptsBothConfirmationsWithoutObservingHealth(Outcome outcome) {
        // INC-06: C-06, applies in UNKNOWN/UP/DOWN.
        var failing = apply(initial(3, 2), Outcome.HTTP_FAILURE, 0);
        failing = apply(failing, Outcome.HTTP_FAILURE, 60);
        var interrupted = apply(failing, outcome, 120);
        assertThat(interrupted.state().failureCount()).isZero();
        assertThat(interrupted.state().firstFailureAt()).isNull();
        assertThat(interrupted.state().confirmedState()).isEqualTo("UNKNOWN");
        assertThat(interrupted.state().lastObservedAt()).isEqualTo(base.plusSeconds(60));
        assertThat(interrupted.state().currentObservation()).isTrue();
        assertThat(apply(interrupted, Outcome.HTTP_FAILURE, 180).state().failureCount()).isEqualTo(1);
        var recovering = apply(down(), Outcome.SUCCESS, 180);
        var decision = decide(recovering, outcome, 240, 240);
        assertThat(decision.transition()).isEqualTo(IncidentStateMachine.Transition.NONE);
        assertThat(decision.monitor().state().recoveryCount()).isZero();
        assertThat(decision.monitor().state().recoveryStartedAt()).isNull();
        assertThat(decision.monitor().state().confirmedState()).isEqualTo("DOWN");
        assertThat(decision.monitor().state().openIncidentId()).isEqualTo(incident);
        assertThat(decision.monitor().state().lastObservedAt()).isEqualTo(base.plusSeconds(180));
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"HTTP_FAILURE", "TIMEOUT", "DNS_FAILURE",
        "CONNECTION_FAILURE", "TLS_FAILURE"})
    void allExternalFailuresCountTowardThreshold(Outcome outcome) {
        // INC-01/06: all MON-05 external failure outcomes.
        var result = decide(initial(1, 1), outcome, 0, 0);
        assertThat(result.transition()).isEqualTo(IncidentStateMachine.Transition.DOWN);
        assertThat(result.monitor().state().failureCount()).isEqualTo(1);
        assertThat(result.monitor().state().lastObservedAt()).isEqualTo(base);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10})
    void configuredThresholdBoundariesConfirmOnlyAtLimit(int threshold) {
        // INC-01/04/08: C-05 and upper bound.
        var monitor = initial(threshold, threshold);
        for (int i = 0; i < threshold; i++) {
            var decision = decide(monitor, Outcome.HTTP_FAILURE, i * 60, i * 60);
            assertThat(decision.transition()).isEqualTo(i == threshold - 1
                    ? IncidentStateMachine.Transition.DOWN : IncidentStateMachine.Transition.NONE);
            monitor = decision.monitor();
        }
        assertThat(monitor.state().firstFailureAt()).isEqualTo(base);
        for (int i = 0; i < threshold; i++) {
            var decision = decide(monitor, Outcome.SUCCESS, (threshold + i) * 60, (threshold + i) * 60);
            assertThat(decision.transition()).isEqualTo(i == threshold - 1
                    ? IncidentStateMachine.Transition.RECOVERY : IncidentStateMachine.Transition.NONE);
            monitor = decision.monitor();
        }
        assertThat(monitor.state().recoveryStartedAt()).isEqualTo(base.plusSeconds(threshold * 60));
        assertThat(monitor.state().confirmedState()).isEqualTo("UP");
    }

    @Test
    void exactFreshnessBoundaryContinuesButOneMicrosecondGapRestarts() {
        // INC-06: L=125; test previous gap separately from HTTP duration.
        var monitor = apply(down(), Outcome.SUCCESS, 180);
        var boundary = decide(monitor, Outcome.SUCCESS, 305, 335);
        assertThat(boundary.transition()).isEqualTo(IncidentStateMachine.Transition.RECOVERY);
        assertThat(boundary.monitor().state().recoveryStartedAt()).isEqualTo(base.plusSeconds(180));
        var result = result(monitor, Outcome.SUCCESS, 306);
        var gap = IncidentStateMachine.apply(monitor, result, base.plusSeconds(305).plusNanos(1000), incident);
        assertThat(gap.transition()).isEqualTo(IncidentStateMachine.Transition.NONE);
        assertThat(gap.monitor().state().recoveryCount()).isEqualTo(1);
        assertThat(gap.monitor().state().recoveryStartedAt()).isEqualTo(base.plusSeconds(306));
        assertThat(gap.monitor().state().openIncidentId()).isEqualTo(incident);
        var failures = apply(apply(initial(3, 2), Outcome.HTTP_FAILURE, 0), Outcome.HTTP_FAILURE, 60);
        var fresh = decide(failures, Outcome.HTTP_FAILURE, 185, 186);
        assertThat(fresh.transition()).isEqualTo(IncidentStateMachine.Transition.DOWN);
        var stale = decide(failures, Outcome.HTTP_FAILURE, 186, 187);
        assertThat(stale.monitor().state().failureCount()).isEqualTo(1);
        assertThat(stale.monitor().state().firstFailureAt()).isEqualTo(base.plusSeconds(187));
        assertThat(stale.monitor().state().confirmedState()).isEqualTo("UNKNOWN");
    }

    private Monitor down() {
        var monitor = initial(3, 2);
        for (int i = 0; i < 3; i++) { monitor = apply(monitor, Outcome.HTTP_FAILURE, i * 60); }
        return monitor;
    }

    private Monitor initial(int failures, int successes) {
        return Monitor.create(UUID.randomUUID(), MonitorConfiguration.validated("state", "https://example.org",
                60, 5, 200, failures, successes, "ops@example.org"), base);
    }

    private Monitor apply(Monitor monitor, Outcome outcome, int second) {
        return decide(monitor, outcome, second, second).monitor();
    }

    private IncidentStateMachine.Decision decide(Monitor monitor, Outcome outcome, int start, int finish) {
        return IncidentStateMachine.apply(monitor, result(monitor, outcome, finish), base.plusSeconds(start), incident);
    }

    private CheckResult result(Monitor monitor, Outcome outcome, int second) {
        return new CheckResult(monitor.id(), UUID.randomUUID(), 1, 1, base.plusSeconds(second),
                base.plusSeconds(second), 0, outcome,
                outcome == Outcome.SUCCESS ? Integer.valueOf(200)
                        : outcome == Outcome.HTTP_FAILURE ? Integer.valueOf(503) : null);
    }
}
