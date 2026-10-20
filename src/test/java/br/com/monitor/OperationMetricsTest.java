package br.com.monitor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.monitoring.CheckScheduler;
import br.com.monitor.monitoring.ScheduleStore;
import br.com.monitor.operation.NotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationMetricsTest {
    @Test
    void scheduledMetricsMeasureRealInFlightDelayDurationAndBoundedOutcomes() throws Exception {
        Instant now = Instant.parse("2026-10-20T12:00:00Z");
        var monitor = Monitor.create(UUID.randomUUID(), MonitorConfiguration.validated("metrics",
                "https://example.org", 60, 5, 200, 1, 1, "ops@example.org"), now.minusSeconds(3));
        var attempt = new ScheduleStore.Attempt(monitor, UUID.randomUUID(), now.minusSeconds(3), now);
        var store = mock(ScheduleStore.class);
        when(store.pending()).thenReturn(List.of());
        when(store.claim(any(), any())).thenReturn(Optional.of(attempt), Optional.empty());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var meters = new SimpleMeterRegistry();
        try (var scheduler = new CheckScheduler(store, (target, id) -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            return new CheckResult(target.id(), id, 1, 1, now, now.plusMillis(125), 125_000_000,
                    CheckResult.Outcome.HTTP_FAILURE, 503);
        }, Clock.fixed(now, ZoneOffset.UTC), 4, meters)) {
            scheduler.tick();
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(meters.get("monitor.checks.in.flight").gauge().value()).isEqualTo(1);
            assertThat(meters.get("monitor.checks.capacity").gauge().value()).isEqualTo(4);
            release.countDown();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(5))).isTrue();
            assertThat(meters.get("monitor.checks.in.flight").gauge().value()).isZero();
            assertThat(meters.get("monitor.checks.scheduling.delay").timer().totalTime(TimeUnit.SECONDS))
                    .isEqualTo(3);
            assertThat(meters.get("monitor.checks.duration").tag("outcome", "HTTP_FAILURE").timer()
                    .totalTime(TimeUnit.MILLISECONDS)).isEqualTo(125);
            assertThat(meters.get("monitor.checks.results").tag("outcome", "HTTP_FAILURE").counter().count())
                    .isEqualTo(1);
            assertThat(meters.getMeters()).hasSize(21);
            assertThat(meters.getMeters().stream().flatMap(m -> m.getId().getTags().stream()))
                    .allMatch(tag -> tag.getKey().equals("outcome")
                            && java.util.Arrays.stream(CheckResult.Outcome.values())
                                    .anyMatch(value -> value.name().equals(tag.getValue())));
        } finally { release.countDown(); }
    }

    @Test
    void notificationCountsAreLiveAndDatabaseErrorsAreUnknownInsteadOfZero() {
        var jdbc = mock(JdbcTemplate.class);
        var meters = new SimpleMeterRegistry();
        try {
            var metrics = new NotificationMetrics(jdbc, meters);
            for (String status : List.of("PENDING", "SENDING", "EXHAUSTED")) {
                when(jdbc.queryForObject(any(String.class), any(Class.class), any(String.class))).thenReturn(7L);
                assertThat(meters.get("monitor.notifications").tag("status", status).gauge().value()).isEqualTo(7);
                when(jdbc.queryForObject(any(String.class), any(Class.class), any(String.class)))
                        .thenThrow(new IllegalStateException("secret"));
                assertThat(meters.get("monitor.notifications").tag("status", status).gauge().value()).isNaN();
            }
            assertThat(metrics).isNotNull();
            assertThat(meters.getMeters()).hasSize(3);
        } finally { meters.close(); }
    }
}
