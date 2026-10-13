package br.com.monitor;

import java.nio.file.Files;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationIT {
    @Test
    void emptyDatabaseIsMigratedExactlyOnce() throws Exception {
        // MON-02-AC02: V1 creates the schema and restarts do not change migration history.
        try (var db = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            db.start();
            var flyway = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            var before = flyway.info().applied();
            assertThat(before).hasSize(1);
            assertThat(before[0].getVersion().getVersion()).isEqualTo("1");
            try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                    var statement = connection.createStatement();
                    var result = statement.executeQuery(
                            "SELECT count(*) FROM pg_namespace WHERE nspname = 'monitor'")) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            var after = flyway.info().applied();
            assertThat(after).hasSize(1);
            assertThat(after[0].getChecksum()).isEqualTo(before[0].getChecksum());
            assertThat(after[0].getInstalledOn()).isEqualTo(before[0].getInstalledOn());
        }
    }

    @Test
    void alteredAppliedMigrationIsRejected(@TempDir java.nio.file.Path directory) throws Exception {
        // MON-02-AC02: do not accept an altered migration as already applied.
        try (var db = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            db.start();
            var migration = directory.resolve("V1__create_monitor_schema.sql");
            try (var source = getClass().getResourceAsStream("/db/migration/V1__create_monitor_schema.sql")) {
                Files.copy(source, migration);
            }
            var flyway = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .locations("filesystem:" + directory).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            Files.writeString(migration, "CREATE SCHEMA altered_schema;");
            assertThatThrownBy(flyway::validate).isInstanceOf(FlywayValidateException.class)
                    .hasMessageContaining("checksum mismatch");
        }
    }
}
