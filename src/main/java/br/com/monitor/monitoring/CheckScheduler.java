package br.com.monitor.monitoring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.concurrent.TimeUnit;
import br.com.monitor.administration.Monitor;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CheckScheduler implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CheckScheduler.class);
    private final ScheduleStore store;
    private final BiFunction<Monitor, UUID, CheckResult> execute;
    private final Clock clock;
    private final int capacity;
    private final MeterRegistry meters;
    private final Map<UUID, Work> running = new LinkedHashMap<>();
    private boolean closed;

    public CheckScheduler(ScheduleStore store, BiFunction<Monitor, UUID, CheckResult> execute, Clock clock,
            int capacity) {
        this(store, execute, clock, capacity, null);
    }

    public CheckScheduler(ScheduleStore store, BiFunction<Monitor, UUID, CheckResult> execute, Clock clock,
            int capacity, MeterRegistry meters) {
        if (capacity < 1 || capacity > 16) {
            throw new IllegalArgumentException("Concurrency must be between 1 and 16");
        }
        this.store = store;
        this.execute = execute;
        this.clock = clock;
        this.capacity = capacity;
        this.meters = meters;
        if (meters != null) {
            meters.gauge("monitor.checks.in.flight", this, CheckScheduler::inFlight);
            meters.gauge("monitor.checks.capacity", this, value -> value.capacity);
            meters.timer("monitor.checks.scheduling.delay");
            for (var outcome : CheckResult.Outcome.values()) {
                meters.counter("monitor.checks.results", "outcome", outcome.name());
                meters.timer("monitor.checks.duration", "outcome", outcome.name());
            }
        }
    }

    public synchronized void tick() {
        if (closed) { return; }
        for (var work : running.values()) {
            if (!work.cancelRequested && !store.current(work.attempt)) {
                work.cancelRequested = true;
                work.thread.interrupt();
            }
        }
        for (var monitorId : store.pending()) {
            if (!running.containsKey(monitorId)) { store.recover(monitorId, now()); }
        }
        while (running.size() < capacity) {
            var claimed = store.claim(now(), running.keySet());
            if (claimed.isEmpty()) { break; }
            var attempt = claimed.orElseThrow();
            if (meters != null) {
                meters.timer("monitor.checks.scheduling.delay").record(
                        Duration.between(attempt.scheduledAt(), attempt.startedAt()).isNegative() ? Duration.ZERO
                                : Duration.between(attempt.scheduledAt(), attempt.startedAt()));
            }
            var work = new Work(attempt);
            work.thread = Thread.ofVirtual().name("scheduled-check-" + attempt.executionId())
                    .unstarted(() -> run(attempt));
            running.put(attempt.monitor().id(), work);
            work.thread.start();
        }
    }

    public synchronized int inFlight() { return running.size(); }

    public synchronized boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!running.isEmpty()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) { return false; }
            java.util.concurrent.TimeUnit.NANOSECONDS.timedWait(this, remaining);
        }
        return true;
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            running.values().forEach(work -> work.thread.interrupt());
        }
        try {
            if (!awaitIdle(Duration.ofSeconds(5))) { LOG.warn("Check shutdown left recoverable reservations"); }
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    private void run(ScheduleStore.Attempt attempt) {
        try {
            CheckResult result;
            try { result = execute.apply(attempt.monitor(), attempt.executionId()); }
            catch (RuntimeException error) {
                result = new CheckResult(attempt.monitor().id(), attempt.executionId(),
                        attempt.monitor().state().configurationVersion(),
                        attempt.monitor().state().executionGeneration(),
                        attempt.startedAt(), now(), 0, CheckResult.Outcome.INTERNAL_ERROR, null);
            }
            boolean interrupted = Thread.interrupted();
            try {
                store.finish(attempt, result);
                if (meters != null) {
                    meters.counter("monitor.checks.results", "outcome", result.outcome().name()).increment();
                    meters.timer("monitor.checks.duration", "outcome", result.outcome().name())
                            .record(result.durationNanos(), TimeUnit.NANOSECONDS);
                }
            }
            finally { if (interrupted) { Thread.currentThread().interrupt(); } }
        } catch (RuntimeException error) {
            LOG.warn("Check recording failed; reservation remains recoverable");
        } finally {
            synchronized (this) {
                running.remove(attempt.monitor().id());
                notifyAll();
            }
        }
    }

    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    private static final class Work {
        private final ScheduleStore.Attempt attempt;
        private Thread thread;
        private boolean cancelRequested;
        private Work(ScheduleStore.Attempt attempt) { this.attempt = attempt; }
    }
}
