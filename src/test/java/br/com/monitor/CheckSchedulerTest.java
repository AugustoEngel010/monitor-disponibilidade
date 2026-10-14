package br.com.monitor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.monitoring.CheckScheduler;
import br.com.monitor.monitoring.ScheduleStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckSchedulerTest {
    private final MutableClock clock = new MutableClock();
    private final ScheduleStore store = mock(ScheduleStore.class);
    private final ConcurrentLinkedQueue<ScheduleStore.Attempt> due = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<CheckResult> results = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<UUID, Thread> threads = new ConcurrentHashMap<>();

    CheckSchedulerTest() {
        when(store.claim(any())).thenAnswer(call -> Optional.ofNullable(due.poll()));
        when(store.pending()).thenReturn(List.of());
        when(store.current(any())).thenReturn(true);
        doAnswer(call -> { results.add(call.getArgument(1)); return null; }).when(store).finish(any(), any());
    }

    @Test
    void concurrentRoundsRespectCapacityAndKeepExcessPendingWhileHealthyFreesSlot() throws Exception {
        var slow = attempt();
        var healthy = attempt();
        var third = attempt();
        due.addAll(List.of(slow, healthy, third));
        var entered = new CountDownLatch(2);
        var releaseSlow = new CountDownLatch(1);
        var releaseHealthy = new CountDownLatch(1);
        var thirdStarted = new CountDownLatch(1);
        BiFunction<Monitor, UUID, CheckResult> execute = (monitor, id) -> {
            threads.put(monitor.id(), Thread.currentThread());
            if (monitor.id().equals(third.monitor().id())) { thirdStarted.countDown(); }
            else {
                entered.countDown(); await(monitor.id().equals(slow.monitor().id()) ? releaseSlow : releaseHealthy);
            }
            return result(monitor, id, Outcome.SUCCESS);
        };
        try (var scheduler = new CheckScheduler(store, execute, clock, 2)) {
            var barrier = new CyclicBarrier(2);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var calls = pool.invokeAll(List.of(() -> { barrier.await(); scheduler.tick(); return true; },
                        () -> { barrier.await(); scheduler.tick(); return true; }));
                calls.get(0).get(); calls.get(1).get();
            }
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            clock.advance(3600);
            scheduler.tick();
            assertThat(scheduler.inFlight()).isEqualTo(2);
            assertThat(due).containsExactly(third);
            assertThat(threads.keySet()).containsExactlyInAnyOrder(slow.monitor().id(), healthy.monitor().id());
            releaseHealthy.countDown();
            join(healthy);
            scheduler.tick();
            assertThat(thirdStarted.await(2, TimeUnit.SECONDS)).isTrue();
            join(third);
            assertThat(results).extracting(CheckResult::monitorId)
                    .containsExactlyInAnyOrder(healthy.monitor().id(), third.monitor().id());
            assertThat(scheduler.inFlight()).isEqualTo(1);
            assertThat(threads.get(slow.monitor().id()).isAlive()).isTrue();
            releaseSlow.countDown();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
        } finally { releaseSlow.countDown(); releaseHealthy.countDown(); }
    }

    @ParameterizedTest
    @EnumSource(value = Outcome.class, names = {"SUCCESS", "HTTP_FAILURE", "TIMEOUT", "CANCELLED", "INTERNAL_ERROR"})
    void everyTerminalOutcomeReleasesCapacityAndPreservesResult(Outcome outcome) throws Exception {
        var first = attempt();
        var next = attempt();
        due.add(first);
        try (var scheduler = new CheckScheduler(store, (monitor, id) -> result(monitor, id, outcome), clock, 1)) {
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(scheduler.inFlight()).isZero();
            assertThat(results).singleElement().satisfies(saved -> {
                assertThat(saved.executionId()).isEqualTo(first.executionId());
                assertThat(saved.outcome()).isEqualTo(outcome);
            });
            due.add(next);
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(results).extracting(CheckResult::monitorId)
                    .containsExactly(first.monitor().id(), next.monitor().id());
        }
    }

    @Test
    void invalidatedGenerationInterruptsButDoesNotFreeCapacityBeforeWorkerExits() throws Exception {
        var first = attempt();
        var resumed = new ScheduleStore.Attempt(first.monitor().pause(clock.instant()).resume(clock.instant()),
                UUID.randomUUID(), clock.instant(), clock.instant());
        due.add(first);
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var exit = new CountDownLatch(1);
        try (var scheduler = new CheckScheduler(store, (monitor, id) -> {
            started.countDown();
            try { exit.await(); }
            catch (InterruptedException error) { interrupted.countDown(); await(exit); }
            return result(monitor, id, Outcome.CANCELLED);
        }, clock, 1)) {
            scheduler.tick();
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            when(store.current(first)).thenReturn(false);
            due.add(resumed);
            scheduler.tick();
            assertThat(interrupted.await(2, TimeUnit.SECONDS)).isTrue();
            scheduler.tick();
            assertThat(scheduler.inFlight()).isEqualTo(1);
            assertThat(due).containsExactly(resumed);
            assertThat(results).isEmpty();
            exit.countDown();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(results).singleElement().extracting(CheckResult::outcome).isEqualTo(Outcome.CANCELLED);
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(results).hasSize(2);
        } finally { exit.countDown(); }
    }

    @Test
    void unexpectedExecutionErrorIsInternalAndReleasesSlot() throws Exception {
        due.add(attempt());
        try (var scheduler = new CheckScheduler(store, (monitor, id) -> {
            throw new IllegalStateException("secret must not be persisted");
        }, clock, 1)) {
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(results).singleElement().satisfies(saved -> {
                assertThat(saved.outcome()).isEqualTo(Outcome.INTERNAL_ERROR);
                assertThat(saved.errorMessage()).isEqualTo("Internal execution error");
                assertThat(saved.outcome().external()).isFalse();
            });
            assertThat(scheduler.inFlight()).isZero();
        }
    }

    @Test
    void persistenceFailureReleasesSlotAndNextRoundRecoversOrphan() throws Exception {
        var attempt = attempt();
        due.add(attempt);
        doThrow(new IllegalStateException("database unavailable")).when(store).finish(any(), any());
        try (var scheduler = new CheckScheduler(store,
                (monitor, id) -> result(monitor, id, Outcome.SUCCESS), clock, 1)) {
            scheduler.tick();
            assertThat(scheduler.awaitIdle(Duration.ofSeconds(2))).isTrue();
            assertThat(scheduler.inFlight()).isZero();
            when(store.pending()).thenReturn(List.of(attempt.monitor().id()));
            var recovered = new ConcurrentLinkedQueue<UUID>();
            doAnswer(call -> { recovered.add(call.getArgument(0)); return null; }).when(store).recover(any(), any());
            scheduler.tick();
            assertThat(recovered).containsExactly(attempt.monitor().id());
        }
    }

    @Test
    void claimFailureDoesNotConsumeCapacity() {
        when(store.claim(any())).thenThrow(new IllegalStateException("database unavailable"));
        try (var scheduler = new CheckScheduler(store,
                (monitor, id) -> result(monitor, id, Outcome.SUCCESS), clock, 1)) {
            assertThatThrownBy(scheduler::tick).isInstanceOf(IllegalStateException.class);
            assertThat(scheduler.inFlight()).isZero();
            assertThat(results).isEmpty();
        }
    }

    @Test
    void shutdownCancelsWorkersAndPreventsNewStarts() throws Exception {
        due.add(attempt());
        var started = new CountDownLatch(1);
        var wait = new CountDownLatch(1);
        var scheduler = new CheckScheduler(store, (monitor, id) -> {
            started.countDown(); await(wait); return result(monitor, id, Outcome.CANCELLED);
        }, clock, 1);
        scheduler.tick();
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        scheduler.close();
        assertThat(scheduler.inFlight()).isZero();
        assertThat(results).singleElement().extracting(CheckResult::outcome).isEqualTo(Outcome.CANCELLED);
        var next = attempt();
        due.add(next);
        scheduler.tick();
        assertThat(due).containsExactly(next);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 17})
    void rejectsInvalidConcurrency(int capacity) {
        assertThatThrownBy(() -> new CheckScheduler(store,
                (monitor, id) -> result(monitor, id, Outcome.SUCCESS), clock, capacity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ScheduleStore.Attempt attempt() {
        var config = MonitorConfiguration.validated("worker", "https://example.org", 60, 5, 200, 3, 2, "ops@e.org");
        return new ScheduleStore.Attempt(Monitor.create(UUID.randomUUID(), config, clock.instant()),
                UUID.randomUUID(), clock.instant(), clock.instant());
    }

    private CheckResult result(Monitor monitor, UUID id, Outcome outcome) {
        Integer status = null;
        if (outcome == Outcome.SUCCESS) { status = 200; }
        else if (outcome == Outcome.HTTP_FAILURE) { status = 503; }
        return new CheckResult(monitor.id(), id, monitor.state().configurationVersion(),
                monitor.state().executionGeneration(), clock.instant(), clock.instant(), 0, outcome, status);
    }

    private void join(ScheduleStore.Attempt attempt) throws InterruptedException {
        var thread = threads.get(attempt.monitor().id());
        thread.join(2000);
        assertThat(thread.isAlive()).isFalse();
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-10-14T12:00:00Z"));
        void advance(long seconds) { time.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public Instant instant() { return time.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }
}
