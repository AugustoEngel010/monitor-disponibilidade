package br.com.monitor;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import br.com.monitor.administration.MonitorProblem;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.query.SummaryQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummaryQueriesIT extends QueryFixture {
    private SummaryQueries queries() { return new SummaryQueries(named, history, clock); }

    @Test
    void eightSuccessesTwoExternalFailuresAndThreeExcludedResultsReturnEightyPercent() {
        for (int id = 1; id <= 8; id++) { check(monitor, id, id, Outcome.SUCCESS, 100_000_000, true); }
        check(monitor, 9, 9, Outcome.HTTP_FAILURE, 900_000_000, true);
        check(monitor, 10, 10, Outcome.TIMEOUT, 1_000_000_000, true);
        check(monitor, 11, 11, Outcome.INTERNAL_ERROR, 0, true);
        check(monitor, 12, 12, Outcome.DESTINATION_BLOCKED, 0, true);
        check(monitor, 13, 13, Outcome.CANCELLED, 0, true);
        check(monitor, 14, -1, Outcome.SUCCESS, 2_000_000_000, false);
        check(monitor, 15, 100, Outcome.SUCCESS, 2_000_000_000, false);
        check(newMonitor(), 16, 1, Outcome.SUCCESS, 2_000_000_000, false);
        var result = queries().summary(monitor.id(), window(0, 100, 0, 20, null, null, null));
        var stats = result.statistics();
        assertThat(stats.metricType()).isEqualTo("CHECK_SUCCESS_SAMPLING");
        assertThat(stats.dataStatus()).isEqualTo("HAS_DATA");
        assertThat(stats.totalChecks()).isEqualTo(13);
        assertThat(stats.validSamples()).isEqualTo(10);
        assertThat(stats.successSamples()).isEqualTo(8);
        assertThat(stats.latencySamples()).isEqualTo(8);
        assertThat(stats.successRatePercent()).isEqualByComparingTo("80.00");
        assertThat(stats.averageResponseTimeMs()).isEqualByComparingTo("100.00");
        assertThat(result.window().from()).isEqualTo(base);
        assertThat(result.window().to()).isEqualTo(base.plusSeconds(100));
        assertThat(result.window().timeZone()).isEqualTo("UTC");
        assertThat(result.generatedAt()).isEqualTo(clock.instant());
        assertThat(result.monitorId()).isEqualTo(monitor.id());
        assertThat(result.coverageKnownFrom()).isEqualTo(monitor.createdAt());
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"HTTP_FAILURE", "TIMEOUT", "DNS_FAILURE",
        "CONNECTION_FAILURE", "TLS_FAILURE"})
    void everyExternalFailureCountsInTheDenominatorAndNeverInLatency(Outcome outcome) {
        check(monitor, 1, 0, Outcome.SUCCESS, 100_000_000, false);
        check(monitor, 2, 10, outcome, 900_000_000, false);
        var stats = queries().summary(monitor.id(), window(0, 100, 0, 20, null, null, null)).statistics();
        assertThat(stats.validSamples()).isEqualTo(2);
        assertThat(stats.successRatePercent()).isEqualByComparingTo("50.00");
        assertThat(stats.averageResponseTimeMs()).isEqualByComparingTo("100.00");
        assertThat(stats.latencySamples()).isEqualTo(1);
    }

    @Test
    void noExternalDataNeverInventsPercentageOrLatencyAndFailureOnlyHasNoLatency() {
        check(monitor, 1, 0, Outcome.INTERNAL_ERROR, 0, false);
        check(monitor, 2, 10, Outcome.CANCELLED, 0, false);
        check(monitor, 3, 20, Outcome.DESTINATION_BLOCKED, 0, false);
        var result = queries().summary(monitor.id(), window(0, 100, 0, 20, null, null, null));
        assertThat(result.statistics().dataStatus()).isEqualTo("NO_DATA");
        assertThat(result.statistics().validSamples()).isZero();
        assertThat(result.statistics().successSamples()).isZero();
        assertThat(result.statistics().latencySamples()).isZero();
        assertThat(result.statistics().totalChecks()).isEqualTo(3);
        assertThat(result.statistics().successRatePercent()).isNull();
        assertThat(result.statistics().averageResponseTimeMs()).isNull();
        assertThat(result.noObservationPeriods()).containsExactly(
                new SummaryQueries.Period(base, base.plusSeconds(100)));
        check(monitor, 4, 30, Outcome.TIMEOUT, 900_000_000, false);
        var failures = queries().summary(monitor.id(), window(30, 40, 0, 20, null, null, null)).statistics();
        assertThat(failures.successRatePercent()).isEqualByComparingTo("0.00");
        assertThat(failures.averageResponseTimeMs()).isNull();
        var empty = queries().summary(monitor.id(), window(200, 300, 0, 20, null, null, null));
        assertThat(empty.statistics().totalChecks()).isZero();
        assertThat(empty.statistics().successRatePercent()).isNull();
    }

    @Test
    void latencyUsesMonotonicDurationsIncludingZeroAndRoundsOnlyAtPresentation() {
        check(monitor, 1, 0, Outcome.SUCCESS, 0, false);
        check(monitor, 2, 10, Outcome.SUCCESS, 1_010_001, false);
        var stats = queries().summary(monitor.id(), window(0, 100, 0, 20, null, null, null)).statistics();
        assertThat(stats.latencySamples()).isEqualTo(2);
        assertThat(stats.averageResponseTimeMs()).isEqualByComparingTo("0.51");
        assertThat(stats.successRatePercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void coverageUnionsClipsAndIncludesObservationsBeforeWindowWithoutAddingSamples() {
        check(monitor, 1, 0, Outcome.SUCCESS, 1, true);
        check(monitor, 2, 200, Outcome.HTTP_FAILURE, 1, true);
        var result = queries().summary(monitor.id(), window(0, 300, 0, 20, null, null, null));
        assertThat(result.noObservationPeriods()).containsExactly(
                new SummaryQueries.Period(base.plusSeconds(125), base.plusSeconds(200)));
        var before = queries().summary(monitor.id(), window(50, 100, 0, 20, null, null, null));
        assertThat(before.noObservationPeriods()).isEmpty();
        assertThat(before.statistics().validSamples()).isZero();
        assertThat(before.statistics().successRatePercent()).isNull();
        check(monitor, 3, 100, Outcome.SUCCESS, 1, true);
        assertThat(queries().summary(monitor.id(), window(0, 300, 0, 20, null, null, null))
                .noObservationPeriods()).isEmpty();
    }

    @Test
    void pauseResumeAndPolicyChangeCutCoverageButNameEditsAndRestartDoNot() {
        check(monitor, 1, 0, Outcome.SUCCESS, 1, true);
        var paused = monitor.pause(base.plusSeconds(30));
        monitors.save(paused);
        var resumed = paused.resume(base.plusSeconds(40));
        monitors.save(resumed);
        // Late results are history only, even if external.
        var late = checks.save(new CheckResult(monitor.id(), uuid(2), 1, 1, base.plusSeconds(45),
                base.plusSeconds(45), 1, Outcome.SUCCESS, 200));
        processor.process(late.executionId());
        check(monitor, 3, 60, Outcome.SUCCESS, 1, true);
        var result = queries().summary(monitor.id(), window(0, 100, 0, 20, null, null, null));
        assertThat(result.noObservationPeriods()).containsExactly(
                new SummaryQueries.Period(base.plusSeconds(30), base.plusSeconds(60)));
        var changedConfig = new br.com.monitor.administration.MonitorConfiguration("renamed", "https://example.org",
                10, 1, 200, 1, 1, "ops@example.org");
        var edited = resumed.edit(changedConfig, base.plusSeconds(80));
        monitors.save(edited);
        check(monitor, 4, 90, Outcome.SUCCESS, 1, true);
        var changed = new SummaryQueries(named, history, clock)
                .summary(monitor.id(), window(70, 120, 0, 20, null, null, null));
        assertThat(changed.noObservationPeriods()).containsExactly(
                new SummaryQueries.Period(base.plusSeconds(80), base.plusSeconds(90)),
                new SummaryQueries.Period(base.plusSeconds(111), base.plusSeconds(120)));
        var nameOnly = new br.com.monitor.administration.MonitorConfiguration("another", "https://example.org",
                10, 1, 200, 1, 1, "new@example.org");
        monitors.save(edited.edit(nameOnly, base.plusSeconds(100)));
        assertThat(queries().summary(monitor.id(), window(70, 120, 0, 20, null, null, null)).noObservationPeriods())
                .isEqualTo(changed.noObservationPeriods());
    }

    @Test
    void unknownHistoricalOrUnappliedResultsDoNotCreateObservationCoverage() {
        check(monitor, 1, 0, Outcome.SUCCESS, 1, false);
        var result = queries().summary(monitor.id(), window(-1100, 100, 0, 20, null, null, null));
        assertThat(result.noObservationPeriods()).containsExactly(
                new SummaryQueries.Period(base.minusSeconds(1100), base.plusSeconds(100)));
        assertThat(result.statistics().validSamples()).isEqualTo(1);
        assertThat(result.label()).isEqualTo("SEM_DADOS");
        assertThat(result.lastObservation()).isNull();
        assertThat(result.lastObservedAt()).isNull();
        assertThat(result.currentIncident()).isNull();
        assertThatThrownBy(() -> queries().summary(uuid(999), window(0, 100, 0, 20, null, null, null)))
                .isInstanceOfSatisfying(MonitorProblem.class, p -> assertThat(p.status()).isEqualTo(404));
    }

    @Test
    void labelsKeepLifecycleConfirmedHealthFreshnessAndConfirmationIndependent() {
        var at = base.plusSeconds(500);
        var now = Clock.fixed(at, ZoneOffset.UTC);
        var query = new SummaryQueries(named, history, now);
        var window = window(0, 100, 0, 20, null, null, null);
        assertThat(query.summary(monitor.id(), window).label()).isEqualTo("SEM_DADOS");
        jdbc.update("UPDATE monitor.monitors SET last_observed_at=?,current_observation=true,failure_count=1",
                Timestamp.from(at.minusSeconds(125)));
        var boundary = query.summary(monitor.id(), window);
        assertThat(boundary.label()).isEqualTo("CONFIRMANDO_QUEDA");
        assertThat(boundary.evidence()).isEqualTo("FRESH");
        assertThat(boundary.recentObservation()).isTrue();
        assertThat(boundary.failureCount()).isEqualTo(1);
        assertThat(boundary.configurationVersion()).isEqualTo(1);
        assertThat(boundary.executionGeneration()).isEqualTo(1);
        assertThat(boundary.nextCheckAt()).isEqualTo(monitor.state().nextCheckAt());
        jdbc.update("UPDATE monitor.monitors SET last_observed_at=?",
                Timestamp.from(at.minusSeconds(125).minusNanos(1000)));
        assertThat(query.summary(monitor.id(), window).label()).isEqualTo("SEM_DADOS_RECENTES");
        jdbc.update("UPDATE monitor.monitors SET last_observed_at=?", Timestamp.from(at.plusSeconds(1)));
        assertThat(query.summary(monitor.id(), window).evidence()).isEqualTo("STALE");
        jdbc.update("UPDATE monitor.monitors SET last_observed_at=?,confirmed_state='UP',failure_count=0,"
                + " recovery_count=1", Timestamp.from(at));
        assertThat(query.summary(monitor.id(), window).label()).isEqualTo("UP");
        assertThat(query.summary(monitor.id(), window).recoveryCount()).isEqualTo(1);
        var current = monitors.find(monitor.id(), false).orElseThrow();
        monitors.save(current.pause(at));
        assertThat(query.summary(monitor.id(), window).label()).isEqualTo("PAUSED");
        assertThat(query.summary(monitor.id(), window).lifecycle()).isEqualTo("PAUSED");
        assertThat(query.summary(monitor.id(), window).confirmedState()).isEqualTo("UP");
        monitors.save(monitors.find(monitor.id(), false).orElseThrow().resume(at.plusSeconds(1)));
        assertThat(query.summary(monitor.id(), window).label()).isEqualTo("SEM_DADOS");
    }

    @Test
    void currentIncidentAndLastAppliedObservationRemainVisibleWhenStaleOrPaused() {
        processor.process(check(monitor, 1, 0, Outcome.HTTP_FAILURE, 100, false).executionId());
        var q = window(0, 100, 0, 20, null, null, null);
        var freshClock = Clock.fixed(base.plusSeconds(1), ZoneOffset.UTC);
        var fresh = new SummaryQueries(named, history, freshClock).summary(monitor.id(), q);
        assertThat(fresh.label()).isEqualTo("DOWN");
        assertThat(fresh.currentIncident().id()).isEqualTo(monitors.find(monitor.id(), false).orElseThrow()
                .state().openIncidentId());
        assertThat(fresh.lastObservation().executionId()).isEqualTo(uuid(1));
        assertThat(fresh.lastObservedAt()).isEqualTo(base);
        assertThat(queries().summary(monitor.id(), q).label()).isEqualTo("SEM_DADOS_RECENTES");
        assertThat(queries().summary(monitor.id(), q).currentIncident().ageSeconds()).isEqualByComparingTo("600");
        jdbc.update("UPDATE monitor.monitors SET recovery_count=1,recovery_started_at=?", Timestamp.from(base));
        assertThat(new SummaryQueries(named, history, freshClock).summary(monitor.id(), q).label())
                .isEqualTo("CONFIRMANDO_RECUPERACAO");
        monitors.save(monitors.find(monitor.id(), false).orElseThrow().pause(base.plusSeconds(10)));
        var paused = queries().summary(monitor.id(), q);
        assertThat(paused.label()).isEqualTo("PAUSED");
        assertThat(paused.currentIncident().id()).isEqualTo(fresh.currentIncident().id());
        assertThat(paused.currentIncident().estimatedDurationSeconds()).isNull();
        assertThat(paused.lastObservation().executionId()).isEqualTo(uuid(1));
    }
}
