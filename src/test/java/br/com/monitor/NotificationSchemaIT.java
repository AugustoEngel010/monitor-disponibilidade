package br.com.monitor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.incident.IncidentProcessor;
import br.com.monitor.incident.IncidentRepository;
import br.com.monitor.monitoring.CheckRepository;
import br.com.monitor.monitoring.CheckResult;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSchemaIT {
    @Test
    void upgradePreservesV5IntentAndChecksumsAndEnforcesDeliveryIntegrity() {
        // MAIL-01/02/03/05: upgrade existing outbox, DB constraints, transactional attempt history.
        try (var db = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            db.start();
            var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            Flyway.configure().dataSource(source).target("5").load().migrate();
            var jdbc = new JdbcTemplate(source);
            var named = new NamedParameterJdbcTemplate(source);
            var manager = new DataSourceTransactionManager(source);
            var monitors = new MonitorRepository(named);
            var checks = new CheckRepository(named);
            var processor = new IncidentProcessor(monitors, checks, new IncidentRepository(named), named, manager);
            var now = Instant.parse("2026-10-15T12:00:00Z");
            var monitor = Monitor.create(UUID.randomUUID(), MonitorConfiguration.validated("schema",
                    "https://example.org", 60, 5, 200, 1, 1, "ops@example.org"), now);
            monitors.insert(monitor);
            var result = checks.save(new CheckResult(monitor.id(), UUID.randomUUID(), 1, 1, now, now,
                    0, CheckResult.Outcome.HTTP_FAILURE, 503));
            processor.process(result.executionId());
            var before = jdbc.queryForMap("SELECT * FROM monitor.notification_intents");
            var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
            // Keep this MON-08 upgrade contract scoped to V5 -> V6 as new migrations are added.
            var flyway = Flyway.configure().dataSource(source).target("6").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version <> '6'"
                    + " ORDER BY installed_rank")).isEqualTo(history);
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_intents")).containsAllEntriesOf(before)
                    .containsEntry("attempt_count", 0).containsEntry("status", "PENDING");
            assertThat(processor.process(result.executionId())).isFalse();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.notification_intents", Integer.class))
                    .isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO monitor.notification_intents"
                    + " (id, transition_id, recipient, monitor_name, monitor_url, first_failure_at, down_confirmed_at)"
                    + " SELECT ?, transition_id, recipient, monitor_name, monitor_url, first_failure_at,"
                    + " down_confirmed_at FROM monitor.notification_intents", UUID.randomUUID()))
                    .isInstanceOf(DataAccessException.class);
            var id = before.get("id");
            assertThatThrownBy(() -> jdbc.update("UPDATE monitor.notification_intents SET attempt_count=6"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE monitor.notification_intents SET status='ACCEPTED'"))
                    .isInstanceOf(DataAccessException.class);
            var tx = new TransactionTemplate(manager);
            tx.executeWithoutResult(status -> {
                jdbc.update("UPDATE monitor.notification_intents SET status='SENDING', attempt_count=1,"
                        + " next_attempt_at=NULL WHERE id=?", id);
                jdbc.update("INSERT INTO monitor.notification_attempts VALUES (?,1,?,NULL,'SENDING',NULL)",
                        id, Timestamp.from(now));
                status.setRollbackOnly();
            });
            assertThat(jdbc.queryForMap("SELECT * FROM monitor.notification_intents"))
                    .containsEntry("status", "PENDING").containsEntry("attempt_count", 0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.notification_attempts", Integer.class))
                    .isZero();
            jdbc.update("INSERT INTO monitor.notification_attempts VALUES (?,1,?,NULL,'SENDING',NULL)",
                    id, Timestamp.from(now));
            assertThatThrownBy(() -> jdbc.update("INSERT INTO monitor.notification_attempts"
                    + " VALUES (?,2,?,NULL,'SENDING',NULL)", id, Timestamp.from(now)))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE monitor.notification_attempts"
                    + " SET outcome='FAILED',finished_at=?,error_code='secret'", Timestamp.from(now)))
                    .isInstanceOf(DataAccessException.class);
        }
    }
}
