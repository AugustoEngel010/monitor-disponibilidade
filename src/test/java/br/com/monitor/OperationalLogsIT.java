package br.com.monitor;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.notification.NotificationStore;
import br.com.monitor.operation.OperationalEvents;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalLogsIT extends QueryFixture {
    @Test
    void committedEventsCorrelateAllFourIdsAndRollbackEmitsNothing() {
        var logger = (Logger) LoggerFactory.getLogger(OperationalEvents.class);
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            var fixture = new NotificationFixture(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()));
            var target = fixture.monitor();
            var result = fixture.apply(target, CheckResult.Outcome.HTTP_FAILURE, 0);
            var deliveryStore = new NotificationStore(named, fixture.manager);
            var delivery = deliveryStore.claim(NotificationFixture.BASE).orElseThrow();
            deliveryStore.finish(delivery, NotificationStore.Result.FAILED, NotificationFixture.BASE);
            var events = new ArrayList<>(capture.list);
            assertThat(events).hasSize(4);
            assertThat(fields(events.get(0))).containsEntry("event", "CHECK_RECORDED")
                    .containsEntry("monitorId", target.id()).containsEntry("executionId", result.executionId())
                    .containsEntry("outcome", "HTTP_FAILURE");
            for (var event : events.subList(1, 4)) {
                assertThat(fields(event)).containsEntry("monitorId", target.id())
                        .containsEntry("executionId", result.executionId())
                        .containsEntry("incidentId", delivery.incidentId())
                        .containsEntry("notificationId", delivery.id());
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).isEqualTo("Operation committed");
                assertThat(fields(event).keySet()).containsExactlyInAnyOrder("event", "monitorId", "executionId",
                        "incidentId", "notificationId", "outcome");
                assertThat(fields(event).values().toString()).doesNotContain("example.org", "Original monitor");
            }
            assertThat(fields(events.get(1))).containsEntry("event", "INCIDENT_TRANSITION")
                    .containsEntry("outcome", "DOWN");
            assertThat(fields(events.get(2))).containsEntry("event", "NOTIFICATION_CLAIMED");
            assertThat(fields(events.get(3))).containsEntry("event", "NOTIFICATION_FINISHED")
                    .containsEntry("outcome", "FAILED");
            var tx = new TransactionTemplate(fixture.manager);
            tx.executeWithoutResult(status -> {
                OperationalEvents.committed(OperationalEvents.Event.CHECK_RECORDED, target.id(), UUID.randomUUID(),
                        null, null, CheckResult.Outcome.INTERNAL_ERROR);
                assertThat(capture.list).hasSize(4);
                status.setRollbackOnly();
            });
            assertThat(capture.list).hasSize(4);
        } finally { logger.detachAppender(capture); capture.stop(); }
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        Map<String, Object> result = new HashMap<>();
        event.getKeyValuePairs().forEach(pair -> result.put(pair.key, pair.value));
        return result;
    }
}
