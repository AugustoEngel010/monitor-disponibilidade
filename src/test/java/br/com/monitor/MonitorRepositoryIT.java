package br.com.monitor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorProblem;
import br.com.monitor.administration.MonitorRepository;
import br.com.monitor.administration.MonitorService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonitorRepositoryIT {
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;
    private MonitorService service;
    private MonitorRepository repository;
    private final MonitorConfiguration config = MonitorConfiguration.validated("Demo", "https://example.org",
            60, 5, 200, 3, 2, "ops@example.org");

    @BeforeAll
    void start() {
        db.start();
        context = open();
        jdbc = context.getBean(JdbcTemplate.class);
        repository = context.getBean(MonitorRepository.class);
        service = context.getBean(MonitorService.class);
        jdbc.execute("CREATE TABLE monitor.history_fixture (monitor_id uuid REFERENCES monitor.monitors(id),"
                + " payload text NOT NULL)");
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE monitor.check_attempts, monitor.history_fixture, monitor.check_results,"
                + " monitor.monitors CASCADE");
    }

    @AfterAll
    void stop() { context.close(); db.stop(); }

    @Test
    void migrationUpgradesV1AndIsNeverReapplied() {
        var history = jdbc.queryForList("SELECT version, checksum, installed_on FROM public.flyway_schema_history"
                + " ORDER BY installed_rank");
        assertThat(history).hasSize(6);
        assertThat(history.get(0).get("version")).isEqualTo("1");
        assertThat(history.get(1).get("version")).isEqualTo("2");
        assertThat(history.get(2).get("version")).isEqualTo("3");
        assertThat(history.get(3).get("version")).isEqualTo("4");
        assertThat(history.get(4).get("version")).isEqualTo("5");
        assertThat(history.get(5).get("version")).isEqualTo("6");
        var flyway = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .defaultSchema("public").load();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForList("SELECT version, checksum, installed_on FROM public.flyway_schema_history"
                + " ORDER BY installed_rank")).isEqualTo(history);
    }

    @Test
    void existingV1DatabaseReceivesV2WithoutChangingItsHistoryOrData() {
        try (var previous = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            previous.start();
            var source = new DriverManagerDataSource(previous.getJdbcUrl(),
                    previous.getUsername(), previous.getPassword());
            var old = Flyway.configure().dataSource(source).defaultSchema("public").target("1").load();
            assertThat(old.migrate().migrationsExecuted).isEqualTo(1);
            var sql = new JdbcTemplate(source);
            sql.execute("CREATE TABLE monitor.previous_data (value integer)");
            sql.execute("INSERT INTO monitor.previous_data VALUES (4242)");
            var history = sql.queryForList("SELECT * FROM public.flyway_schema_history WHERE version='1'");
            var current = Flyway.configure().dataSource(source).defaultSchema("public").target("2").load();
            assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(current.migrate().migrationsExecuted).isZero();
            assertThat(sql.queryForList("SELECT * FROM public.flyway_schema_history WHERE version='1'"))
                    .isEqualTo(history);
            assertThat(sql.queryForObject("SELECT value FROM monitor.previous_data", Integer.class)).isEqualTo(4242);
            assertThat(sql.queryForObject("SELECT count(*) FROM monitor.monitors", Integer.class)).isZero();
        }
    }

    @Test
    void completeStateAndConfigurationSurviveContextRestart() {
        var instant = Instant.parse("2026-10-13T12:00:00Z");
        var monitor = new Monitor(UUID.randomUUID(), config, new Monitor.State("ACTIVE", "DOWN", 0, 1,
                instant.minusSeconds(120), instant.minusSeconds(5), instant.minusSeconds(5), true,
                UUID.randomUUID(), 3, 5, instant.plusSeconds(55)), instant.minusSeconds(200), instant);
        repository.insert(Monitor.create(monitor.id(), config, monitor.createdAt()));
        IncidentFixtures.open(jdbc, monitor.id(), monitor.state().openIncidentId(), 3);
        repository.save(monitor);
        assertThat(service.get(monitor.id())).isEqualTo(monitor);
        context.close();
        context = open();
        jdbc = context.getBean(JdbcTemplate.class);
        repository = context.getBean(MonitorRepository.class);
        service = context.getBean(MonitorService.class);
        assertThat(service.get(monitor.id())).isEqualTo(monitor);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history", Integer.class))
                .isEqualTo(6);
    }

    @Test
    void rejectedMixedEditIsAtomicAndLifecyclePreservesHistory() {
        var monitor = service.create(config);
        var incident = UUID.randomUUID();
        IncidentFixtures.open(jdbc, monitor.id(), incident, 1);
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='DOWN', open_incident_id=?, recovery_count=1,"
                + " recovery_started_at=now(), last_observed_at=now(), current_observation=true WHERE id=?",
                incident, monitor.id());
        jdbc.update("INSERT INTO monitor.history_fixture VALUES (?, 'historic result')", monitor.id());
        var before = service.get(monitor.id());
        assertThatThrownBy(() -> service.edit(monitor.id(), MonitorConfiguration.validated("mixed",
                "https://other.org", null, null, null, null, null, "new@example.org")))
                .isInstanceOfSatisfying(MonitorProblem.class, error -> assertThat(error.status()).isEqualTo(409));
        assertThat(service.get(monitor.id())).isEqualTo(before);
        var nameOnly = service.edit(monitor.id(), MonitorConfiguration.validated("new", config.url(),
                null, null, null, null, null, "new@example.org"));
        assertThat(nameOnly.state()).isEqualTo(before.state());
        var paused = service.pause(monitor.id());
        assertThat(service.pause(monitor.id())).isEqualTo(paused);
        var resumed = service.resume(monitor.id());
        assertThat(service.resume(monitor.id())).isEqualTo(resumed);
        assertThat(resumed.state().openIncidentId()).isEqualTo(incident);
        assertThat(resumed.state().confirmedState()).isEqualTo("DOWN");
        assertThat(resumed.state().recoveryCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT payload FROM monitor.history_fixture WHERE monitor_id=?",
                String.class, monitor.id())).isEqualTo("historic result");
    }

    @Test
    void policyChangeIsPersistedAndKeepsHistoricalRows() {
        var monitor = service.create(config);
        jdbc.update("INSERT INTO monitor.history_fixture VALUES (?, 'original')", monitor.id());
        var changed = service.edit(monitor.id(), MonitorConfiguration.validated("new", "http://other.org",
                10, 9, 599, 10, 10, "new@example.org"));
        assertThat(service.get(monitor.id())).isEqualTo(changed);
        assertThat(changed.state().configurationVersion()).isEqualTo(2);
        assertThat(changed.state().confirmedState()).isEqualTo("UNKNOWN");
        assertThat(changed.evidence(Instant.now())).isEqualTo("NEVER_OBSERVED");
        assertThat(jdbc.queryForObject("SELECT payload FROM monitor.history_fixture", String.class))
                .isEqualTo("original");
    }

    @Test
    void concurrentRegistrationsRespectLimitIncludingPausedMonitors() throws Exception {
        for (int i = 0; i < 99; i++) { service.pause(service.create(config).id()); }
        Callable<Integer> create = () -> {
            try { service.create(config); return 201; }
            catch (MonitorProblem error) { return error.status(); }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(create, create));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.monitors", Integer.class)).isEqualTo(100);
    }

    @Test
    void concurrentPauseOnlyAdvancesGenerationOnce() throws Exception {
        var monitor = service.create(config);
        Callable<Monitor> pause = () -> service.pause(monitor.id());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(pause, pause));
            assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
        }
        assertThat(service.get(monitor.id()).state().executionGeneration()).isEqualTo(2);
        assertThat(service.get(monitor.id()).state().lifecycle()).isEqualTo("PAUSED");
    }

    @Test
    void serviceRejectsInvalidPaginationFieldsBeforeQuerying() {
        assertThatThrownBy(() -> service.list(-1, 0)).isInstanceOfSatisfying(MonitorProblem.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error.errors()).containsOnlyKeys("page", "size");
        });
        assertThatThrownBy(() -> service.list(0, 101)).isInstanceOfSatisfying(MonitorProblem.class, error -> {
            assertThat(error.status()).isEqualTo(400);
            assertThat(error.errors()).containsOnlyKeys("size");
        });
    }

    private ConfigurableApplicationContext open() {
        return new SpringApplicationBuilder(MonitorApplication.class).run("--server.port=0",
                "--monitor.scheduler.enabled=false",
                "--spring.datasource.url=" + db.getJdbcUrl(), "--spring.datasource.username=" + db.getUsername(),
                "--spring.datasource.password=" + db.getPassword(), "--ADMIN_USERNAME=" + UUID.randomUUID(),
                "--ADMIN_PASSWORD=" + UUID.randomUUID());
    }
}
