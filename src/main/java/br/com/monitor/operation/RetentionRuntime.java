package br.com.monitor.operation;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "monitor.retention.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionRuntime implements DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(RetentionRuntime.class);
    private final RetentionService retention;
    private final int pollMillis;
    private final ScheduledExecutorService polling = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("retention-cleanup").factory());

    public RetentionRuntime(RetentionService retention, Environment environment) {
        this.retention = retention;
        pollMillis = RetentionService.setting(environment, "monitor.retention.poll-ms", 3600000, 1000, 86400000);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        polling.scheduleWithFixedDelay(this::tick, pollMillis, pollMillis, TimeUnit.MILLISECONDS);
    }

    public void tick() {
        if (polling.isShutdown()) { return; }
        try {
            RetentionService.Cleanup result;
            do {
                result = retention.clean();
                LOG.atInfo().addKeyValue("event", "RETENTION_COMPLETED")
                        .addKeyValue("historyAvailableFrom", result.historyAvailableFrom())
                        .addKeyValue("checksRemoved", result.checks())
                        .addKeyValue("incidentsRemoved", result.incidents())
                        .addKeyValue("epochsRemoved", result.epochs()).addKeyValue("more", result.more())
                        .log("Retention batch committed");
            } while (result.more() && !Thread.currentThread().isInterrupted());
        } catch (RuntimeException error) {
            LOG.atWarn().addKeyValue("event", "RETENTION_FAILED").log("Retention batch rolled back");
        }
    }

    @Override
    public void destroy() {
        polling.shutdownNow();
        try { polling.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
