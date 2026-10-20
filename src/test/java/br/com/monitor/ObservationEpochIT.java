package br.com.monitor;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationEpochIT {
    @Test
    void epochsFollowAdministrativeBoundariesAndRollbackWithoutChangingPreviousMigrations() {
        try (var db = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            db.start();
            var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            var previous = Flyway.configure().dataSource(source).target("6").load();
            previous.migrate();
            var checksums = java.util.Arrays.stream(previous.info().applied()).map(m -> m.getChecksum()).toList();
            var jdbc = new JdbcTemplate(source);
            var repository = new MonitorRepository(new NamedParameterJdbcTemplate(source));
            var base = Instant.parse("2026-10-18T12:00:00Z");
            var config = MonitorConfiguration.validated("epoch", "https://example.org", 60, 5, 200, 3, 2,
                    "ops@example.org");
            var original = Monitor.create(UUID.randomUUID(), config, base);
            repository.insert(original);
            var changed = Monitor.create(UUID.randomUUID(), config, base).pause(base.plusSeconds(30));
            repository.insert(changed);
            var current = Flyway.configure().dataSource(source).target("7").load();
            assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(java.util.Arrays.stream(current.info().applied()).limit(6).map(m -> m.getChecksum()).toList())
                    .isEqualTo(checksums);
            assertThat(jdbc.queryForObject("SELECT started_at FROM monitor.observation_epochs WHERE monitor_id=?",
                    Timestamp.class, original.id()).toInstant()).isEqualTo(base);
            assertThat(jdbc.queryForObject("SELECT started_at FROM monitor.observation_epochs WHERE monitor_id=?",
                    Timestamp.class, changed.id()).toInstant()).isAfter(base.plusSeconds(30));
            var paused = original.pause(base.plusSeconds(30));
            repository.save(paused);
            repository.save(paused.pause(base.plusSeconds(31)));
            var resumed = paused.resume(base.plusSeconds(40));
            repository.save(resumed);
            var renamed = new MonitorConfiguration("renamed", config.url(), 60, 5, 200, 3, 2, "new@example.org");
            var edited = resumed.edit(renamed, base.plusSeconds(50));
            repository.save(edited);
            assertThat(jdbc.queryForList("SELECT lifecycle FROM monitor.observation_epochs WHERE monitor_id=?"
                    + " ORDER BY execution_generation", String.class, original.id()))
                    .containsExactly("ACTIVE", "PAUSED", "ACTIVE");
            assertThat(jdbc.queryForList("SELECT ended_at FROM monitor.observation_epochs WHERE monitor_id=?"
                    + " AND ended_at IS NOT NULL ORDER BY execution_generation", Timestamp.class, original.id()))
                    .containsExactly(Timestamp.from(base.plusSeconds(30)), Timestamp.from(base.plusSeconds(40)));
            var policy = new MonitorConfiguration("renamed", config.url(), 10, 1, 200, 3, 2, "new@example.org");
            var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
            tx.executeWithoutResult(status -> {
                repository.save(edited.edit(policy, base.plusSeconds(60)));
                status.setRollbackOnly();
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.observation_epochs WHERE monitor_id=?",
                    Integer.class, original.id())).isEqualTo(3);
            repository.save(edited.edit(policy, base.plusSeconds(60)));
            assertThat(jdbc.queryForObject("SELECT freshness_seconds FROM monitor.observation_epochs"
                    + " WHERE monitor_id=? AND execution_generation=4", Integer.class, original.id())).isEqualTo(21);
            assertThat(current.migrate().migrationsExecuted).isZero();
        }
    }
}
