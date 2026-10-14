package br.com.monitor.monitoring;

import java.time.Clock;
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
@ConditionalOnProperty(name = "monitor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerRuntime implements DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(SchedulerRuntime.class);
    private final CheckScheduler scheduler;
    private final long pollMillis;
    private final ScheduledExecutorService polling;

    public SchedulerRuntime(ScheduleStore store, CheckService checks, Clock clock, Environment environment) {
        int capacity = environment.getProperty("monitor.scheduler.concurrency", Integer.class, 4);
        pollMillis = environment.getProperty("monitor.scheduler.poll-ms", Long.class, 250L);
        if (pollMillis <= 0) { throw new IllegalArgumentException("Scheduler poll interval must be positive"); }
        scheduler = new CheckScheduler(store, checks::executeAndRecord, clock, capacity);
        polling = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("check-poll").factory());
    }

    public CheckScheduler scheduler() { return scheduler; }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        polling.scheduleWithFixedDelay(() -> {
            try { scheduler.tick(); }
            catch (RuntimeException error) { LOG.warn("Check scheduling failed; persisted work remains pending"); }
        }, 0, pollMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() {
        polling.shutdownNow();
        scheduler.close();
    }
}
