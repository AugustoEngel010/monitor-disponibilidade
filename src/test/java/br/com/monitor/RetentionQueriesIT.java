package br.com.monitor;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.operation.RetentionService;
import br.com.monitor.query.SummaryQueries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionQueriesIT extends QueryFixture {
    @BeforeEach
    void resetRetention() { jdbc.update("UPDATE monitor.retention_state SET history_available_from=NULL"); }

    @Test
    void removedWindowHasNoSamplesEvenWhenOperationalEvidenceIsProtected() {
        var result = check(monitor, 1, -10, CheckResult.Outcome.SUCCESS, 100_000_000, false);
        processor.process(result.executionId());
        cleanup();
        var q = window(-60, -1, 0, 20, null, null, null);
        var summary = new SummaryQueries(named, history, clock).summary(monitor.id(), q);
        assertThat(checks.find(result.executionId())).contains(result);
        assertThat(summary.lastObservation().executionId()).isEqualTo(result.executionId());
        assertThat(summary.historyAvailableFrom()).isEqualTo(base);
        assertThat(summary.retainedOutPeriods()).containsExactly(new SummaryQueries.Period(q.from(), q.to()));
        assertThat(summary.noObservationPeriods()).isEmpty();
        assertThat(summary.statistics().dataStatus()).isEqualTo("NO_DATA");
        assertThat(summary.statistics().totalChecks()).isZero();
        assertThat(summary.statistics().validSamples()).isZero();
        assertThat(summary.statistics().successSamples()).isZero();
        assertThat(summary.statistics().latencySamples()).isZero();
        assertThat(summary.statistics().successRatePercent()).isNull();
        assertThat(summary.statistics().averageResponseTimeMs()).isNull();
        var page = history.checks(monitor.id(), q);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.historyAvailableFrom()).isEqualTo(base);
        assertThat(page.retainedOutPeriods()).isEqualTo(summary.retainedOutPeriods());
    }

    @Test
    void crossingWindowSeparatesRetentionFromExecutionGapsAndPreservesPendingChains() {
        var old = check(monitor, 1, -10, CheckResult.Outcome.HTTP_FAILURE, 0, false);
        processor.process(old.executionId());
        var latest = check(monitor, 2, 10, CheckResult.Outcome.HTTP_FAILURE, 0, false);
        processor.process(latest.executionId());
        cleanup();
        var q = window(-60, 60, 0, 20, null, null, null);
        var summary = new SummaryQueries(named, history, clock).summary(monitor.id(), q);
        assertThat(summary.statistics().totalChecks()).isEqualTo(1);
        assertThat(summary.statistics().validSamples()).isEqualTo(1);
        assertThat(summary.statistics().successRatePercent()).isEqualByComparingTo("0.00");
        assertThat(summary.statistics().averageResponseTimeMs()).isNull();
        assertThat(summary.retainedOutPeriods()).containsExactly(new SummaryQueries.Period(q.from(), base));
        assertThat(summary.noObservationPeriods())
                .containsExactly(new SummaryQueries.Period(base, base.plusSeconds(10)));
        assertThat(history.checks(monitor.id(), q).items()).extracting(value -> value.executionId())
                .containsExactly(latest.executionId());
        var incidentPage = history.incidents(monitor.id(), q);
        assertThat(incidentPage.items()).hasSize(1);
        assertThat(incidentPage.items().getFirst().status()).isEqualTo("OPEN");
        assertThat(incidentPage.historyAvailableFrom()).isEqualTo(base);
        assertThat(incidentPage.retainedOutPeriods()).isEqualTo(summary.retainedOutPeriods());
        var notificationPage = history.notifications(monitor.id(), q);
        assertThat(notificationPage.items()).hasSize(1);
        assertThat(notificationPage.items().getFirst().status()).isEqualTo("PENDING");
        assertThat(notificationPage.historyAvailableFrom()).isEqualTo(base);
        assertThat(notificationPage.retainedOutPeriods()).isEqualTo(summary.retainedOutPeriods());
        assertThat(summary.currentIncident().id()).isEqualTo(incidentPage.items().getFirst().id());
    }

    @Test
    void coverageCannotCrossCutoffFromAnOlderProtectedObservation() {
        var old = check(monitor, 1, -10, CheckResult.Outcome.SUCCESS, 100_000_000, false);
        processor.process(old.executionId());
        cleanup();
        var q = window(0, 60, 0, 20, null, null, null);
        var summary = new SummaryQueries(named, history, clock).summary(monitor.id(), q);
        assertThat(summary.historyAvailableFrom()).isEqualTo(base);
        assertThat(summary.retainedOutPeriods()).isEmpty();
        assertThat(summary.noObservationPeriods()).containsExactly(new SummaryQueries.Period(q.from(), q.to()));
        assertThat(summary.statistics().dataStatus()).isEqualTo("NO_DATA");
    }

    @Test
    void beforeCleanupOriginalContractAndPreWindowCoverageRemainUnchanged() {
        check(monitor, 1, -10, CheckResult.Outcome.SUCCESS, 100_000_000, true);
        var q = window(0, 60, 0, 20, null, null, null);
        var summary = new SummaryQueries(named, history, clock).summary(monitor.id(), q);
        assertThat(summary.historyAvailableFrom()).isNull();
        assertThat(summary.retainedOutPeriods()).isEqualTo(List.of());
        assertThat(summary.noObservationPeriods()).isEmpty();
        assertThat(summary.statistics().dataStatus()).isEqualTo("NO_DATA");
        assertThat(history.checks(monitor.id(), q).historyAvailableFrom()).isNull();
    }

    @Test
    void newObservationsStillCoverWindowsStartingAfterCutoffAndHistoryPagesRetainMetadata() {
        check(monitor, 1, 10, CheckResult.Outcome.SUCCESS, 100_000_000, true);
        cleanup();
        var q = window(20, 60, 9, 1, null, null, null);
        var summary = new SummaryQueries(named, history, clock).summary(monitor.id(), q);
        assertThat(summary.retainedOutPeriods()).isEmpty();
        assertThat(summary.noObservationPeriods()).isEmpty();
        assertThat(summary.statistics().successRatePercent()).isNull();
        var page = history.checks(monitor.id(), q);
        assertThat(page.items()).isEmpty();
        assertThat(page.historyAvailableFrom()).isEqualTo(base);
        assertThat(page.retainedOutPeriods()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT history_available_from FROM monitor.retention_state",
                Timestamp.class).toInstant()).isEqualTo(base);
    }

    private void cleanup() {
        new RetentionService(named, new DataSourceTransactionManager(jdbc.getDataSource()),
                Clock.fixed(base.plusSeconds(86400), ZoneOffset.UTC),
                new MockEnvironment().withProperty("monitor.retention.days", "1")).clean();
    }
}
