package br.com.monitor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.notification.NotificationDelivery;
import br.com.monitor.notification.NotificationStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static br.com.monitor.NotificationFixture.BASE;
import static br.com.monitor.notification.NotificationStore.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationStoreIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private JdbcTemplate jdbc;
    private NotificationFixture fixture;
    private NotificationStore store;

    @BeforeAll
    void start() {
        db.start();
        var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = new JdbcTemplate(source);
        fixture = new NotificationFixture(jdbc, new DataSourceTransactionManager(source));
        store = new NotificationStore(fixture.named, fixture.manager);
    }

    @BeforeEach
    void clear() { jdbc.execute("TRUNCATE monitor.monitors CASCADE"); }

    @AfterAll
    void stop() { db.stop(); }

    @Test
    void fiveFailuresHaveExactProgressiveDelaysStableIdentityAndTerminalExhaustion() {
        var id = fixture.down(fixture.monitor());
        Instant due = BASE;
        for (int number = 1; number <= 5; number++) {
            assertThat(store.claim(due.minusMillis(1))).isEmpty();
            var delivery = store.claim(due).orElseThrow();
            assertThat(delivery.id()).isEqualTo(id);
            assertThat(delivery.attempt()).isEqualTo(number);
            assertThat(delivery.messageId()).isEqualTo("<notification-" + id + "@monitor.local>");
            assertThat(row(id)).containsEntry("status", "SENDING").containsEntry("attempt_count", number)
                    .containsEntry("next_attempt_at", null);
            assertThat(store.claim(due)).isEmpty();
            var ended = due.plusSeconds(2);
            store.finish(delivery, Result.FAILED, ended);
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_attempts"
                    + " WHERE notification_id=? AND attempt_number=?", id, number))
                    .containsEntry("started_at", Timestamp.from(due))
                    .containsEntry("finished_at", Timestamp.from(ended))
                    .containsEntry("outcome", "FAILED").containsEntry("error_code", "SMTP_FAILURE");
            if (number < 5) {
                due = ended.plusSeconds(30L << (number - 1));
                assertThat(row(id)).containsEntry("status", "PENDING")
                        .containsEntry("next_attempt_at", Timestamp.from(due)).containsEntry("completed_at", null);
            } else {
                assertThat(row(id)).containsEntry("status", "EXHAUSTED")
                        .containsEntry("completed_at", Timestamp.from(ended)).containsEntry("next_attempt_at", null);
            }
        }
        assertThat(store.claim(due.plusSeconds(10000))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.notification_attempts", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void concurrentClaimsReserveOneAttemptAndCompletionIsIdempotent() throws Exception {
        var id = fixture.down(fixture.monitor());
        var barrier = new CyclicBarrier(4);
        Callable<java.util.Optional<NotificationDelivery>> claim = () -> { barrier.await(); return store.claim(BASE); };
        try (var pool = Executors.newFixedThreadPool(4)) {
            var calls = pool.invokeAll(List.of(claim, claim, claim, claim));
            var deliveries = new java.util.ArrayList<NotificationDelivery>();
            for (var call : calls) { call.get().ifPresent(deliveries::add); }
            assertThat(deliveries).hasSize(1);
            var delivery = deliveries.getFirst();
            store.finish(delivery, Result.ACCEPTED, BASE.plusSeconds(1));
            var before = row(id);
            store.finish(delivery, Result.FAILED, BASE.plusSeconds(2));
            assertThat(row(id)).isEqualTo(before).containsEntry("status", "ACCEPTED");
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_attempts"))
                    .containsEntry("outcome", "ACCEPTED").containsEntry("error_code", null);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"notification_attempts", "notification_intents"})
    void failureInEitherClaimWriteLeavesNoPartialReservation(String table) {
        var id = fixture.down(fixture.monitor());
        reject(table);
        try { assertThatThrownBy(() -> store.claim(BASE)).isInstanceOf(DataAccessException.class); }
        finally { allow(table); }
        assertThat(row(id)).containsEntry("status", "PENDING").containsEntry("attempt_count", 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.notification_attempts", Integer.class)).isZero();
        assertThat(store.claim(BASE).orElseThrow().attempt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"notification_attempts", "notification_intents"})
    void failureInEitherCompletionWriteIsRecoverableWithoutConsumingAnotherAttempt(String table) {
        var id = fixture.down(fixture.monitor());
        var delivery = store.claim(BASE).orElseThrow();
        reject(table);
        try { assertThatThrownBy(() -> store.finish(delivery, Result.ACCEPTED, BASE.plusSeconds(1)))
                .isInstanceOf(DataAccessException.class); }
        finally { allow(table); }
        assertThat(row(id)).containsEntry("status", "SENDING").containsEntry("attempt_count", 1);
        assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_attempts"))
                .containsEntry("outcome", "SENDING").containsEntry("finished_at", null);
        store.finish(delivery, Result.ACCEPTED, BASE.plusSeconds(2));
        assertThat(row(id)).containsEntry("status", "ACCEPTED").containsEntry("attempt_count", 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 5})
    void restartConsumesOrphanAttemptWithUncertainOutcomeAndFiniteRetry(int attempt) {
        var id = fixture.down(fixture.monitor());
        Instant due = BASE;
        for (int n = 1; n < attempt; n++) {
            var delivery = store.claim(due).orElseThrow();
            store.finish(delivery, Result.FAILED, due);
            due = due.plusSeconds(30L << (n - 1));
        }
        var orphan = store.claim(due).orElseThrow();
        var restarted = new NotificationStore(fixture.named, fixture.manager);
        var now = due.plusSeconds(10);
        restarted.recover(now);
        assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_attempts WHERE attempt_number=?", attempt))
                .containsEntry("notification_id", id).containsEntry("started_at", Timestamp.from(due))
                .containsEntry("finished_at", Timestamp.from(now)).containsEntry("outcome", "UNCERTAIN")
                .containsEntry("error_code", "RESTART_UNCERTAIN");
        var before = row(id);
        restarted.recover(now.plusSeconds(1));
        assertThat(row(id)).isEqualTo(before).containsEntry("attempt_count", attempt)
                .containsEntry("status", attempt == 5 ? "EXHAUSTED" : "PENDING");
        if (attempt < 5) {
            var next = now.plusSeconds(30L << (attempt - 1));
            assertThat(restarted.claim(next.minusMillis(1))).isEmpty();
            var delivery = restarted.claim(next).orElseThrow();
            assertThat(delivery.id()).isEqualTo(orphan.id());
            assertThat(delivery.messageId()).isEqualTo(orphan.messageId());
            assertThat(delivery.attempt()).isEqualTo(attempt + 1);
        } else { assertThat(restarted.claim(now.plusSeconds(10000))).isEmpty(); }
    }

    @Test
    void pendingSurvivesReconstructionAndUncertainTimeoutUsesSameBackoff() {
        var id = fixture.down(fixture.monitor());
        var restarted = new NotificationStore(fixture.named, fixture.manager);
        restarted.recover(BASE);
        assertThat(row(id)).containsEntry("attempt_count", 0).containsEntry("status", "PENDING");
        var delivery = restarted.claim(BASE).orElseThrow();
        restarted.finish(delivery, Result.UNCERTAIN, BASE.plusSeconds(3));
        assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_attempts"))
                .containsEntry("outcome", "UNCERTAIN").containsEntry("error_code", "SMTP_UNCERTAIN");
        assertThat(restarted.claim(BASE.plusSeconds(33).minusMillis(1))).isEmpty();
        assertThat(restarted.claim(BASE.plusSeconds(33)).orElseThrow().id()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "EXHAUSTED"})
    void recoveryWaitsForDownTerminalEvenAfterRecipientEditButOtherIncidentsProceed(String terminal) {
        var monitor = fixture.monitor();
        var downId = fixture.down(monitor);
        jdbc.update("UPDATE monitor.monitors SET alert_recipient='new@example.org',name='Changed' WHERE id=?",
                monitor.id());
        fixture.apply(monitor, CheckResult.Outcome.SUCCESS, 60);
        var down = store.claim(BASE).orElseThrow();
        assertThat(down.id()).isEqualTo(downId);
        assertThat(down.recipient()).isEqualTo("ops@example.org");
        assertThat(down.monitorName()).isEqualTo("Original monitor");
        assertThat(store.claim(BASE)).isEmpty();
        var otherId = fixture.down(fixture.monitor());
        var other = store.claim(BASE).orElseThrow();
        assertThat(other.id()).isEqualTo(otherId);
        store.finish(other, Result.ACCEPTED, BASE);
        Instant now = BASE;
        if (terminal.equals("ACCEPTED")) { store.finish(down, Result.ACCEPTED, now); }
        else {
            for (int n = 1; n <= 5; n++) {
                store.finish(down, Result.FAILED, now);
                if (n < 5) {
                    assertThat(store.claim(now)).isEmpty();
                    now = now.plusSeconds(30L << (n - 1));
                    down = store.claim(now).orElseThrow();
                    assertThat(down.id()).isEqualTo(downId);
                }
            }
        }
        var recovery = store.claim(now).orElseThrow();
        assertThat(recovery.type()).isEqualTo("RECOVERY");
        assertThat(recovery.incidentId()).isEqualTo(down.incidentId());
        assertThat(recovery.recipient()).isEqualTo("new@example.org");
        assertThat(recovery.monitorName()).isEqualTo("Changed");
        assertThat(row(downId)).containsEntry("status", terminal);
    }

    private java.util.Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("SELECT * FROM monitor.notification_intents WHERE id=?", id);
    }

    private void reject(String table) {
        jdbc.execute("ALTER TABLE monitor." + table + " ADD CONSTRAINT reject_delivery CHECK (false) NOT VALID");
    }

    private void allow(String table) {
        jdbc.execute("ALTER TABLE monitor." + table + " DROP CONSTRAINT reject_delivery");
    }
}
