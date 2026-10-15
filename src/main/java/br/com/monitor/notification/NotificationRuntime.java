package br.com.monitor.notification;

import java.time.Clock;
import java.time.Instant;
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
@ConditionalOnProperty(name = "monitor.notifications.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRuntime implements DisposableBean {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationRuntime.class);
    private final NotificationStore store;
    private final SmtpDelivery smtp;
    private final Clock clock;
    private final long pollMillis;
    private final ScheduledExecutorService polling;
    private volatile boolean stopped;
    private boolean reconciled;
    private NotificationDelivery active;
    private NotificationStore.Result result;
    private Instant finishedAt;

    public NotificationRuntime(NotificationStore store, SmtpDelivery smtp, Clock clock, Environment environment) {
        this.store = store;
        this.smtp = smtp;
        this.clock = clock;
        try {
            pollMillis = Long.parseLong(environment.getProperty("monitor.notifications.poll-ms", "250"));
            if (pollMillis < 1 || pollMillis > 60000) { throw new IllegalArgumentException(); }
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid notification polling interval");
        }
        polling = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("notification-delivery").factory());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        polling.scheduleWithFixedDelay(() -> {
            try { tick(); }
            catch (RuntimeException error) {
                LOG.warn("Notification persistence failed; durable work remains recoverable");
            }
        }, 0, pollMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void tick() {
        if (stopped) { return; }
        if (!reconciled) { store.recover(clock.instant()); reconciled = true; }
        if (active == null) {
            try { active = store.claim(clock.instant()).orElse(null); }
            catch (RuntimeException error) {
                // A connection loss during commit may leave a reservation whose reply was lost.
                reconciled = false;
                throw error;
            }
        }
        if (active == null || stopped) { return; }
        if (result == null) {
            try { result = smtp.send(active); }
            catch (RuntimeException error) { result = NotificationStore.Result.UNCERTAIN; }
            finishedAt = clock.instant();
        }
        // A failed commit retries this result; it must never repeat SMTP in the same attempt.
        store.finish(active, result, finishedAt);
        active = null;
        result = null;
        finishedAt = null;
    }

    @Override
    public void destroy() {
        stopped = true;
        polling.shutdownNow();
        try { polling.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
