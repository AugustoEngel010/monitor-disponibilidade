package br.com.monitor;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.UUID;
import br.com.monitor.administration.MonitorProblem;
import br.com.monitor.monitoring.CheckResult.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryQueriesIT extends QueryFixture {
    @Test
    void checksUseHalfOpenWindowOutcomeAndStableTimestampUuidOrderAcrossPages() {
        for (int id = 1; id <= 5; id++) { check(monitor, id, 10, Outcome.SUCCESS, 100, false); }
        check(monitor, 6, 11, Outcome.HTTP_FAILURE, 200, false);
        check(monitor, 7, 12, Outcome.SUCCESS, 300, false);
        check(newMonitor(), 8, 10, Outcome.SUCCESS, 100, false);
        var ids = new ArrayList<UUID>();
        for (int page = 0; page < 3; page++) {
            var result = history.checks(monitor.id(), window(10, 12, page, 2, "SUCCESS", null, null));
            assertThat(result.totalElements()).isEqualTo(5);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.page()).isEqualTo(page);
            assertThat(result.size()).isEqualTo(2);
            result.items().forEach(item -> ids.add(item.executionId()));
        }
        assertThat(ids).containsExactly(uuid(5), uuid(4), uuid(3), uuid(2), uuid(1));
        var all = history.checks(monitor.id(), window(10, 12, 0, 20, null, null, null));
        assertThat(all.items().getFirst().executionId()).isEqualTo(uuid(6));
        var check = all.items().getFirst();
        assertThat(check.monitorId()).isEqualTo(monitor.id());
        assertThat(check.configurationVersion()).isEqualTo(1);
        assertThat(check.executionGeneration()).isEqualTo(1);
        assertThat(check.startedAt()).isEqualTo(base.plusSeconds(11));
        assertThat(check.finishedAt()).isEqualTo(base.plusSeconds(11));
        assertThat(check.durationNanos()).isEqualTo(200);
        assertThat(check.outcome()).isEqualTo("HTTP_FAILURE");
        assertThat(check.httpStatus()).isEqualTo(503);
        assertThat(check.errorMessage()).isEqualTo("Unexpected HTTP status");
    }

    @Test
    void emptyAndHugePagesRetainTotalsAndUnknownMonitorIs404ForEveryResource() {
        check(monitor, 1, 10, Outcome.SUCCESS, 0, false);
        assertThat(history.checks(monitor.id(), window(0, 5, 0, 20, null, null, null)).totalElements()).isZero();
        var beyond = history.checks(monitor.id(), window(0, 20, Integer.MAX_VALUE, 100, null, null, null));
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(1);
        assertThat(beyond.totalPages()).isEqualTo(1);
        assertThat(history.incidents(monitor.id(), window(0, 600, 0, 20, null, null, null)).items()).isEmpty();
        assertThat(history.notifications(monitor.id(), window(0, 600, 0, 20, null, null, null)).items()).isEmpty();
        for (var resource : new String[] {"checks", "incidents", "notifications"}) {
            assertThatThrownBy(() -> {
                var q = window(0, 600, 0, 20, null, null, null);
                switch (resource) {
                    case "checks" -> history.checks(UUID.randomUUID(), q);
                    case "incidents" -> history.incidents(UUID.randomUUID(), q);
                    default -> history.notifications(UUID.randomUUID(), q);
                }
            }).isInstanceOfSatisfying(MonitorProblem.class, problem -> {
                assertThat(problem.status()).isEqualTo(404);
                assertThat(problem.code()).isEqualTo("MONITOR_NOT_FOUND");
            });
        }
    }

    @Test
    void incidentsKeepObservationConfirmationAndFractionalDurationSeparateFromOpenAge() {
        for (int id = 1; id <= 3; id++) {
            jdbc.update("INSERT INTO monitor.incidents VALUES (?,?,1,?,?,?,?)", uuid(id), monitor.id(),
                    Timestamp.from(base.plusMillis(100)), Timestamp.from(base.plusSeconds(120)),
                    id == 3 ? null : Timestamp.from(base.plusSeconds(181)),
                    id == 3 ? null : Timestamp.from(base.plusSeconds(240)));
        }
        var ids = new ArrayList<UUID>();
        for (int page = 0; page < 3; page++) {
            var result = history.incidents(monitor.id(), window(120, 121, page, 1, null, null, null));
            assertThat(result.totalElements()).isEqualTo(3);
            ids.add(result.items().getFirst().id());
        }
        assertThat(ids).containsExactly(uuid(3), uuid(2), uuid(1));
        var open = history.incidents(monitor.id(), window(0, 600, 0, 20, null, "OPEN", null));
        assertThat(open.totalElements()).isEqualTo(1);
        assertThat(open.items().getFirst().status()).isEqualTo("OPEN");
        assertThat(open.items().getFirst().estimatedDurationSeconds()).isNull();
        assertThat(open.items().getFirst().ageSeconds()).isEqualByComparingTo("599.9");
        var closed = history.incidents(monitor.id(), window(0, 600, 0, 20, null, "CLOSED", null));
        assertThat(closed.totalElements()).isEqualTo(2);
        var item = closed.items().getFirst();
        assertThat(item.monitorId()).isEqualTo(monitor.id());
        assertThat(item.configurationVersion()).isEqualTo(1);
        assertThat(item.status()).isEqualTo("CLOSED");
        assertThat(item.firstFailureAt()).isEqualTo(base.plusMillis(100));
        assertThat(item.downConfirmedAt()).isEqualTo(base.plusSeconds(120));
        assertThat(item.recoveryStartedAt()).isEqualTo(base.plusSeconds(181));
        assertThat(item.recoveredAt()).isEqualTo(base.plusSeconds(240));
        assertThat(item.estimatedDurationSeconds()).isEqualByComparingTo("180.9");
        assertThat(item.ageSeconds()).isNull();
        assertThat(history.incidents(monitor.id(), window(0, 120, 0, 20, null, null, null)).items()).isEmpty();
        assertThat(history.incidents(monitor.id(), window(0, 600, 99, 20, null, null, null)).items()).isEmpty();
    }

    @Test
    void notificationPagesIncludeFrozenRecipientAndOrderedSanitizedAttempts() {
        var down = check(monitor, 1, 0, Outcome.HTTP_FAILURE, 0, false);
        processor.process(down.executionId());
        processor.process(check(monitor, 2, 60, Outcome.SUCCESS, 0, false).executionId());
        jdbc.update("UPDATE monitor.state_transitions SET confirmed_at=?", Timestamp.from(base));
        var originalIds = jdbc.queryForList("SELECT id FROM monitor.notification_intents ORDER BY id", UUID.class);
        for (int i = 0; i < originalIds.size(); i++) {
            jdbc.update("UPDATE monitor.notification_intents SET id=? WHERE id=?", uuid(10 + i), originalIds.get(i));
        }
        var id = uuid(11);
        jdbc.update("UPDATE monitor.notification_intents SET attempt_count=2 WHERE id=?", id);
        jdbc.update("INSERT INTO monitor.notification_attempts VALUES (?,2,?,?,'UNCERTAIN','SMTP_UNCERTAIN')",
                id, Timestamp.from(base.plusSeconds(30)), Timestamp.from(base.plusSeconds(31)));
        jdbc.update("INSERT INTO monitor.notification_attempts VALUES (?,1,?,?,'FAILED','SMTP_FAILURE')",
                id, Timestamp.from(base.plusSeconds(1)), Timestamp.from(base.plusSeconds(2)));
        var first = history.notifications(monitor.id(), window(0, 1, 0, 1, null, "PENDING", null));
        var second = history.notifications(monitor.id(), window(0, 1, 1, 1, null, null, null));
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items().getFirst().id()).isEqualTo(uuid(11));
        assertThat(second.items().getFirst().id()).isEqualTo(uuid(10));
        var item = first.items().getFirst();
        assertThat(item.monitorId()).isEqualTo(monitor.id());
        assertThat(item.transitionId()).isNotNull();
        assertThat(item.incidentId()).isEqualTo(jdbc.queryForObject("SELECT id FROM monitor.incidents", UUID.class));
        assertThat(item.recipient()).isEqualTo("ops@example.org");
        assertThat(item.status()).isEqualTo("PENDING");
        assertThat(item.confirmedAt()).isEqualTo(base);
        assertThat(item.attemptCount()).isEqualTo(2);
        assertThat(item.nextAttemptAt()).isNotNull();
        assertThat(item.completedAt()).isNull();
        assertThat(item.attempts()).extracting(a -> a.attemptNumber()).containsExactly(1, 2);
        var attempt = item.attempts().get(1);
        assertThat(attempt.startedAt()).isEqualTo(base.plusSeconds(30));
        assertThat(attempt.finishedAt()).isEqualTo(base.plusSeconds(31));
        assertThat(attempt.outcome()).isEqualTo("UNCERTAIN");
        assertThat(attempt.errorCode()).isEqualTo("SMTP_UNCERTAIN");
        assertThat(history.notifications(monitor.id(), window(0, 1, 2, 1, null, null, null)).items()).isEmpty();
        assertThat(history.notifications(monitor.id(), window(-1, 0, 0, 20, null, null, null)).items()).isEmpty();
        assertThat(history.notifications(monitor.id(), window(0, 1, 0, 20, null, "ACCEPTED", null)).items()).isEmpty();
        var recovery = history.notifications(monitor.id(), window(0, 1, 0, 20, null, null, "RECOVERY"));
        assertThat(recovery.totalElements()).isEqualTo(1);
        assertThat(recovery.items().getFirst().type()).isEqualTo("RECOVERY");
    }
}
