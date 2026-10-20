package br.com.monitor;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionMigrationIT {
    @Test
    void v8PreservesV7ChecksumsAndDataAndIsAppliedOnce() {
        try (var db = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            db.start();
            var source = new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword());
            var previous = Flyway.configure().dataSource(source).target("7").load();
            previous.migrate();
            var before = Arrays.stream(previous.info().applied()).map(m -> m.getChecksum()).toList();
            var sql = new JdbcTemplate(source);
            sql.execute("CREATE TABLE monitor.retention_sentinel (value integer)");
            sql.execute("INSERT INTO monitor.retention_sentinel VALUES (4242)");
            var current = Flyway.configure().dataSource(source).load();
            assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(Arrays.stream(current.info().applied()).limit(7).map(m -> m.getChecksum()).toList())
                    .isEqualTo(before);
            assertThat(current.migrate().migrationsExecuted).isZero();
            assertThat(sql.queryForObject("SELECT value FROM monitor.retention_sentinel", Integer.class))
                    .isEqualTo(4242);
            assertThat(sql.queryForObject("SELECT history_available_from FROM monitor.retention_state",
                    java.sql.Timestamp.class)).isNull();
            assertThat(sql.queryForObject("SELECT count(*) FROM monitor.retired_executions", Integer.class)).isZero();
            assertThat(sql.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname='monitor'"
                    + " AND indexname IN ('check_results_retention_idx','incidents_retention_idx',"
                    + "'notification_transition_idx','notification_status_idx','observation_epochs_retention_idx')",
                    Integer.class)).isEqualTo(5);
        }
    }
}
