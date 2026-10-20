package br.com.monitor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorProblem;
import br.com.monitor.administration.MonitorService;
import br.com.monitor.monitoring.CheckScheduler;
import br.com.monitor.monitoring.CheckService;
import br.com.monitor.monitoring.ScheduleStore;
import br.com.monitor.notification.NotificationRuntime;
import br.com.monitor.notification.NotificationStore;
import br.com.monitor.notification.SmtpDelivery;
import br.com.monitor.operation.RetentionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapacityScenario {
    @Test
    void controlledLocalCapacityAndNotificationBacklog() throws Exception {
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 128);
        var handlers = Executors.newVirtualThreadPerTaskExecutor();
        http.setExecutor(handlers);
        for (String mode : new String[] {"healthy", "slow", "failure", "timeout"}) {
            http.createContext("/" + mode, exchange -> {
                try {
                    if (mode.equals("slow")) { Thread.sleep(300); }
                    if (mode.equals("timeout")) { Thread.sleep(3000); }
                    exchange.sendResponseHeaders(mode.equals("failure") ? 503 : 200, -1);
                } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
        }
        http.start();
        try (var smtp = new ServerSocket(0, 128, java.net.InetAddress.getLoopbackAddress());
                var db = new PostgreSQLContainer<>("postgres:17.6-alpine")
                        .withCreateContainerCmdModifier(command -> command.getHostConfig()
                                .withMemory(512L * 1024 * 1024).withNanoCPUs(1_000_000_000L))) {
            var smtpConnections = new AtomicInteger();
            var smtpThread = Thread.ofPlatform().daemon().name("capacity-smtp").start(() -> {
                while (!smtp.isClosed()) {
                    try (var connection = smtp.accept()) {
                        smtpConnections.incrementAndGet();
                        connection.getOutputStream().write("421 Controlled SMTP unavailable\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
                    } catch (IOException error) { if (!smtp.isClosed()) { throw new IllegalStateException(error); } }
                }
            });
            db.start();
            String origin = "http://127.0.0.1:" + http.getAddress().getPort();
            try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(
                    MonitorApplication.class)
                    .run("--server.port=0", "--spring.datasource.url=" + db.getJdbcUrl(),
                            "--spring.datasource.username=" + db.getUsername(),
                            "--spring.datasource.password=" + db.getPassword(),
                            "--ADMIN_USERNAME=capacity-test", "--ADMIN_PASSWORD=capacity-test-only",
                            "--monitor.scheduler.enabled=false", "--monitor.notifications.enabled=false",
                            "--monitor.retention.enabled=false", "--monitor.demo-origin=" + origin,
                            "--monitor.smtp.host=127.0.0.1", "--monitor.smtp.port=" + smtp.getLocalPort())) {
                measure(context, origin, smtpConnections);
            }
            smtp.close();
            smtpThread.join(1000);
        } finally { http.stop(0); handlers.shutdownNow(); }
    }

    private void measure(ServletWebServerApplicationContext context, String origin, AtomicInteger smtpConnections)
            throws Exception {
        var monitors = context.getBean(MonitorService.class);
        String[] modes = {"healthy", "slow", "failure", "timeout"};
        for (int i = 0; i < 100; i++) { monitors.create(configuration(origin, modes[i % modes.length])); }
        assertThatThrownBy(() -> monitors.create(configuration(origin, "healthy")))
                .isInstanceOfSatisfying(MonitorProblem.class,
                        error -> assertThat(error.code()).isEqualTo("MONITOR_LIMIT"));
        var jdbc = context.getBean(JdbcTemplate.class);
        var meters = context.getBean(MeterRegistry.class);
        var checks = context.getBean(CheckService.class);
        var store = context.getBean(ScheduleStore.class);
        var deliveries = context.getBean(NotificationStore.class);
        var smtp = context.getBean(SmtpDelivery.class);
        Clock clock = Clock.systemUTC();
        var active = ConcurrentHashMap.<UUID>newKeySet();
        var overlaps = new AtomicInteger();
        int peak = 0;
        int pendingPeak = 0;
        Instant started = clock.instant();
        long startedNanos = System.nanoTime();
        try (var scheduler = new CheckScheduler(store, (target, id) -> {
            if (!active.add(target.id())) { overlaps.incrementAndGet(); }
            try { return checks.executeAndRecord(target, id); }
            finally { active.remove(target.id()); }
        }, clock, 4, meters)) {
            var notifications = new NotificationRuntime(deliveries, smtp, clock, new MockEnvironment());
            try {
                while (System.nanoTime() - startedNanos < Duration.ofSeconds(60).toNanos()) {
                    scheduler.tick();
                    notifications.tick();
                    peak = Math.max(peak, scheduler.inFlight());
                    pendingPeak = Math.max(pendingPeak,
                            (int) meters.get("monitor.notifications").tag("status", "PENDING").gauge().value());
                    assertThat(scheduler.inFlight()).isLessThanOrEqualTo(4);
                    Thread.sleep(250);
                }
            } finally { notifications.destroy(); }
        }
        double duration = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
        var report = new LinkedHashMap<String, Object>();
        report.put("startedAtUtc", started.toString());
        report.put("durationSecondsIncludingShutdown", duration);
        report.put("monitors", 100);
        report.put("intervalSeconds", 10);
        report.put("timeoutSeconds", 2);
        report.put("concurrency", 4);
        report.put("pollMillis", 250);
        report.put("targets", Map.of("healthy", "25 x 200 immediate", "slow", "25 x 200 after 300ms",
                "failure", "25 x 503 immediate", "timeout", "25 x headers after 3000ms, client timeout 2000ms"));
        report.put("runnerProcessors", Runtime.getRuntime().availableProcessors());
        report.put("runnerHeapMaxBytes", Runtime.getRuntime().maxMemory());
        report.put("runnerMemoryLimitBytes", 2L * 1024 * 1024 * 1024);
        report.put("databaseCpuLimit", 1);
        report.put("databaseMemoryLimitBytes", 512L * 1024 * 1024);
        report.put("peakInFlight", peak);
        report.put("overlaps", overlaps.get());
        report.put("notificationPendingPeak", pendingPeak);
        report.put("smtpConnectionsDuringTimedPhase", smtpConnections.get());
        report.put("notificationPendingAfterTimedPhase", count(jdbc, "notification_intents WHERE status='PENDING'"));
        var lag = meters.get("monitor.checks.scheduling.delay").timer();
        report.put("scheduleDelayCount", lag.count());
        report.put("scheduleDelayMaxSeconds", lag.max(TimeUnit.SECONDS));
        report.put("scheduleDelayMeanSeconds", lag.mean(TimeUnit.SECONDS));
        report.put("outcomes", jdbc.queryForList("SELECT outcome,count(*) AS count,"
                + " round(avg(duration_nanos)/1000000,3) AS mean_ms FROM monitor.check_results"
                + " GROUP BY outcome ORDER BY outcome"));
        assertThat(duration).isGreaterThanOrEqualTo(60);
        assertThat(peak).isEqualTo(4);
        assertThat(overlaps.get()).isZero();
        assertThat(active).isEmpty();
        assertThat(count(jdbc, "monitors")).isEqualTo(100);
        assertThat(count(jdbc, "check_attempts")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT monitor_id) FROM monitor.check_results", Integer.class))
                .isEqualTo(100);
        assertThat(jdbc.queryForList("SELECT DISTINCT outcome FROM monitor.check_results", String.class))
                .contains("SUCCESS", "HTTP_FAILURE", "TIMEOUT").doesNotContain("INTERNAL_ERROR", "DESTINATION_BLOCKED");
        assertThat(lag.max(TimeUnit.SECONDS)).isGreaterThan(0);
        assertThat(pendingPeak).isEqualTo(50);
        assertThat(count(jdbc, "notification_intents WHERE status='PENDING'")).isEqualTo(50);
        assertThat(count(jdbc, "notification_intents WHERE status='SENDING'")).isZero();
        report.put("acceleratedPhase", "After timing, advance logical clock by 1 hour per retry; real local SMTP 421");
        Instant accelerated = clock.instant();
        while (count(jdbc, "notification_intents WHERE status='PENDING'") > 0) {
            accelerated = accelerated.plusSeconds(3600);
            for (int i = 0; i < 50; i++) {
                var delivery = deliveries.claim(accelerated);
                if (delivery.isEmpty()) { break; }
                var value = delivery.orElseThrow();
                deliveries.finish(value, smtp.send(value), accelerated);
            }
        }
        assertThat(meters.get("monitor.notifications").tag("status", "EXHAUSTED").gauge().value()).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT max(attempt_count) FROM monitor.notification_intents", Integer.class))
                .isEqualTo(5);
        report.put("notificationExhaustedAfterAcceleratedPhase", 50);
        report.put("smtpConnectionsTotal", smtpConnections.get());
        var cleanup = new RetentionService(context.getBean(NamedParameterJdbcTemplate.class),
                context.getBean(PlatformTransactionManager.class),
                Clock.fixed(clock.instant().plus(Duration.ofDays(31)), ZoneOffset.UTC), new MockEnvironment()).clean();
        assertThat(count(jdbc, "incidents WHERE recovered_at IS NULL")).isEqualTo(50);
        report.put("checksRetiredAfter31DayAdvance", cleanup.checks());
        report.put("receiptRowBytes", jdbc.queryForObject(
                "SELECT avg(pg_column_size(r)) FROM monitor.retired_executions r", Double.class));
        report.put("receiptRelationBytesWithIndex", jdbc.queryForObject(
                "SELECT pg_total_relation_size('monitor.retired_executions')", Long.class));
        Files.writeString(Path.of("target/capacity.json"), new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(report));
        System.out.println("CAPACITY_RESULT " + new ObjectMapper().writeValueAsString(report));
    }

    private MonitorConfiguration configuration(String origin, String mode) {
        return MonitorConfiguration.validated("capacity-" + mode, origin + "/" + mode, 10, 2, 200, 1, 1,
                "ops@example.org");
    }

    private int count(JdbcTemplate jdbc, String tableAndPredicate) {
        return jdbc.queryForObject("SELECT count(*) FROM monitor." + tableAndPredicate, Integer.class);
    }
}
