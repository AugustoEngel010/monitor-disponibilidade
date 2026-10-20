package br.com.monitor;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.notification.NotificationStore;
import br.com.monitor.operation.RetentionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetentionIT extends QueryFixture {
    @BeforeEach
    void resetWatermark() { jdbc.update("UPDATE monitor.retention_state SET history_available_from=NULL"); }

    @Test
    void cutoffIsStrictAtMicrosecondPrecisionAndReplayCannotRecreateDeletedEvidence() {
        var old = result(base.minusNanos(1000), true);
        var exact = result(base, true);
        var after = result(base.plusNanos(1000), true);
        result(base.plusSeconds(1), true);
        var cleaned = retention(base, 100).clean();
        assertThat(cleaned.checks()).isEqualTo(1);
        assertThat(cleaned.historyAvailableFrom()).isEqualTo(base);
        assertThat(checks.find(old.executionId())).isEmpty();
        assertThat(checks.find(exact.executionId())).contains(exact);
        assertThat(checks.find(after.executionId())).contains(after);
        assertThat(checks.retired(old.executionId())).isTrue();
        assertThat(processor.process(old.executionId())).isFalse();
        assertThatThrownBy(() -> checks.save(old)).hasMessageContaining("Execution history was retired");
        assertThatThrownBy(() -> checks.save(new CheckResult(monitor.id(), old.executionId(), 1, 1,
                base.plusSeconds(10), base.plusSeconds(10), 0, CheckResult.Outcome.SUCCESS, 200)))
                .hasMessageContaining("Execution history was retired");
        assertThat(checks.find(old.executionId())).isEmpty();
        assertThat(retention(base, 100).clean().checks()).isZero();
    }

    @Test
    void openIncidentsAndWholePendingOrSendingNotificationChainsSurvive() {
        var fixture = fixture();
        var open = fixture.monitor();
        UUID down = fixture.down(open);
        var closed = fixture.monitor();
        fixture.down(closed);
        fixture.apply(closed, CheckResult.Outcome.SUCCESS, 10);
        var store = new NotificationStore(named, fixture.manager);
        var sending = store.claim(NotificationFixture.BASE).orElseThrow();
        assertThat(retention(base, 100).clean().incidents()).isZero();
        assertThat(count("incidents")).isEqualTo(2);
        assertThat(count("notification_intents")).isEqualTo(3);
        assertThat(count("state_transitions")).isEqualTo(3);
        assertThat(count("notification_attempts")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM monitor.notification_intents WHERE id=?",
                String.class, sending.id())).isEqualTo("SENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.notification_intents WHERE id=?",
                Integer.class, down)).isEqualTo(1);
        assertThat(monitors.find(open.id(), false).orElseThrow().state().openIncidentId()).isNotNull();
    }

    @Test
    void closedChainExpiresOnlyAfterEveryDeliveryCompletedStrictlyBeforeCutoff() {
        var fixture = fixture();
        var target = fixture.monitor();
        fixture.down(target);
        fixture.apply(target, CheckResult.Outcome.SUCCESS, 10);
        var store = new NotificationStore(named, fixture.manager);
        var down = store.claim(NotificationFixture.BASE).orElseThrow();
        UUID downExecution = jdbc.queryForObject("SELECT execution_id FROM monitor.state_transitions"
                + " WHERE incident_id=? AND type='DOWN'", UUID.class, down.incidentId());
        store.finish(down, NotificationStore.Result.ACCEPTED, base.minusNanos(1000));
        assertThat(retention(base, 100).clean().incidents()).isZero();
        var recovery = store.claim(base).orElseThrow();
        store.finish(recovery, NotificationStore.Result.ACCEPTED, base);
        assertThat(retention(base, 100).clean().incidents()).isZero();
        assertThat(count("notification_attempts")).isEqualTo(2);
        assertThat(retention(base.plusNanos(1000), 100).clean().incidents()).isEqualTo(1);
        assertThat(count("incidents")).isZero();
        assertThat(count("state_transitions")).isZero();
        assertThat(count("notification_intents")).isZero();
        assertThat(count("notification_attempts")).isZero();
        assertThat(checks.find(downExecution)).isEmpty();
    }

    @Test
    void lastObservationAndLatestInternalApplicationPreserveStateAndOrdering() {
        var fixture = fixture();
        var target = fixture.monitor();
        var old = fixture.apply(target, CheckResult.Outcome.SUCCESS, 0);
        var observed = fixture.apply(target, CheckResult.Outcome.SUCCESS, 10);
        Instant internalAt = NotificationFixture.BASE.plusSeconds(20);
        var internal = checks.save(new CheckResult(target.id(), UUID.randomUUID(), 1, 1, internalAt, internalAt,
                0, CheckResult.Outcome.INTERNAL_ERROR, null));
        processor.process(internal.executionId());
        var before = monitors.find(target.id(), false);
        retention(base, 100).clean();
        assertThat(checks.find(old.executionId())).isEmpty();
        assertThat(checks.find(observed.executionId())).contains(observed);
        assertThat(checks.find(internal.executionId())).contains(internal);
        assertThat(monitors.find(target.id(), false)).isEqualTo(before);
        assertThat(incidents.lastAppliedStart(internal)).contains(internalAt);
    }

    @Test
    void unprocessedAndReservedChecksAreNotEligible() {
        var unprocessed = result(base.minusSeconds(3), false);
        var reserved = result(base.minusSeconds(2), true);
        result(base.minusSeconds(1), true);
        jdbc.update("INSERT INTO monitor.check_attempts VALUES (?,?,1,1,?,?)", monitor.id(), reserved.executionId(),
                Timestamp.from(base.minusSeconds(2)), Timestamp.from(base.minusSeconds(2)));
        assertThat(retention(base, 100).clean().checks()).isZero();
        assertThat(checks.find(unprocessed.executionId())).contains(unprocessed);
        assertThat(checks.find(reserved.executionId())).contains(reserved);
        assertThat(count("check_attempts")).isEqualTo(1);
    }

    @Test
    void batchIsBoundedAndWatermarkNeverMovesBackWhenClockOrTtlChanges() {
        for (int i = 0; i < 6; i++) { result(base.minusSeconds(10 - i), true); }
        assertThat(retention(base, 2).clean().checks()).isEqualTo(2);
        assertThat(count("check_results")).isEqualTo(4);
        assertThat(retention(base.minusSeconds(60), 2).clean().checks()).isEqualTo(2);
        assertThat(retention(base.minusSeconds(60), 2).clean().historyAvailableFrom()).isEqualTo(base);
        assertThat(count("check_results")).isEqualTo(1);
        assertThat(count("retired_executions")).isEqualTo(5);
        var longer = new RetentionService(named, manager(), Clock.fixed(base.plusSeconds(86400), ZoneOffset.UTC),
                new MockEnvironment().withProperty("monitor.retention.days", "30"));
        assertThat(longer.clean().historyAvailableFrom()).isEqualTo(base);
    }

    @Test
    void endedEpochsExpireButProtectedEvidenceKeepsItsEpoch() {
        var protectedCheck = result(base.minusSeconds(900), false);
        monitors.save(monitor.pause(base.minusSeconds(800)));
        monitors.save(monitors.find(monitor.id(), false).orElseThrow().resume(base.minusSeconds(700)));
        assertThat(retention(base, 100).clean().epochs()).isEqualTo(1);
        assertThat(checks.find(protectedCheck.executionId())).contains(protectedCheck);
        assertThat(count("observation_epochs")).isEqualTo(2);
    }

    @Test
    void rollbackRestoresPayloadReceiptsAndWatermark() {
        var old = result(base.minusSeconds(2), true);
        result(base.minusSeconds(1), true);
        jdbc.execute("CREATE FUNCTION monitor.reject_cleanup() RETURNS trigger LANGUAGE plpgsql AS $$"
                + " BEGIN RAISE EXCEPTION 'controlled cleanup failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_cleanup BEFORE DELETE ON monitor.check_results"
                + " FOR EACH ROW EXECUTE FUNCTION monitor.reject_cleanup()");
        try {
            assertThatThrownBy(() -> retention(base, 100).clean()).hasMessageContaining("controlled cleanup failure");
            assertThat(checks.find(old.executionId())).contains(old);
            assertThat(count("retired_executions")).isZero();
            assertThat(count("result_applications")).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT history_available_from FROM monitor.retention_state",
                    Timestamp.class)).isNull();
        } finally {
            jdbc.execute("DROP TRIGGER reject_cleanup ON monitor.check_results");
            jdbc.execute("DROP FUNCTION monitor.reject_cleanup()");
        }
        assertThat(retention(base, 100).clean().checks()).isEqualTo(1);
    }

    @Test
    void invalidRetentionSettingsFailWithoutEchoingInput() {
        for (String key : new String[] {"monitor.retention.days", "monitor.retention.batch-size"}) {
            for (String value : new String[] {"0", "-1", "10001", "secret-value"}) {
                assertThatThrownBy(() -> new RetentionService(named, manager(), clock,
                        new MockEnvironment().withProperty(key, value)))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Invalid retention configuration");
            }
        }
    }

    @Test
    void recoveredAtBoundaryAndExhaustedNotificationsFollowTheSameStrictPolicy() {
        UUID incident = UUID.randomUUID();
        IncidentFixtures.open(jdbc, monitor.id(), incident, 1);
        jdbc.update("UPDATE monitor.incidents SET recovered_at=?,recovery_started_at=? WHERE id=?",
                Timestamp.from(base), Timestamp.from(base), incident);
        assertThat(retention(base, 100).clean().incidents()).isZero();
        assertThat(retention(base.plusNanos(1000), 100).clean().incidents()).isEqualTo(1);
        var fixture = fixture();
        var target = fixture.monitor();
        fixture.down(target);
        fixture.apply(target, CheckResult.Outcome.SUCCESS, 10);
        var store = new NotificationStore(named, fixture.manager);
        for (int attempt = 0; attempt < 5; attempt++) {
            Instant time = NotificationFixture.BASE.plusSeconds(attempt * 1000L);
            var down = store.claim(time).orElseThrow();
            assertThat(down.type()).isEqualTo("DOWN");
            store.finish(down, NotificationStore.Result.FAILED, time);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.notification_intents WHERE status='EXHAUSTED'",
                Integer.class)).isEqualTo(1);
        var recovery = store.claim(NotificationFixture.BASE.plusSeconds(5000)).orElseThrow();
        store.finish(recovery, NotificationStore.Result.ACCEPTED, NotificationFixture.BASE.plusSeconds(5000));
        assertThat(retention(base, 100).clean().incidents()).isEqualTo(1);
        assertThat(count("notification_intents")).isZero();
    }

    private DataSourceTransactionManager manager() { return new DataSourceTransactionManager(jdbc.getDataSource()); }
    private NotificationFixture fixture() { return new NotificationFixture(jdbc, manager()); }
    private RetentionService retention(Instant cutoff, int batch) {
        return new RetentionService(named, manager(), Clock.fixed(cutoff.plusSeconds(86400), ZoneOffset.UTC),
                new MockEnvironment().withProperty("monitor.retention.days", "1")
                        .withProperty("monitor.retention.batch-size", Integer.toString(batch)));
    }
    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM monitor." + table, Integer.class);
    }
    private CheckResult result(Instant at, boolean applied) {
        var result = checks.save(new CheckResult(monitor.id(), UUID.randomUUID(), 1, 1, at, at,
                100_000_000, CheckResult.Outcome.SUCCESS, 200));
        if (applied) {
            jdbc.update("INSERT INTO monitor.result_applications VALUES (?,'APPLIED',?)",
                    result.executionId(), Timestamp.from(at));
        }
        return result;
    }
}
