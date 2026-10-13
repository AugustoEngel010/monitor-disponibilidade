package br.com.monitor.administration;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitorService {
    private final MonitorRepository repository;
    private final Clock clock;

    public MonitorService(MonitorRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Bean
    public static Clock monitorClock() { return Clock.systemUTC(); }

    public Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }

    @Transactional
    public Monitor create(MonitorConfiguration configuration) {
        repository.lockCreation();
        if (repository.count() >= 100) { throw new MonitorProblem(409, "MONITOR_LIMIT", Map.of()); }
        var monitor = Monitor.create(UUID.randomUUID(), configuration, now());
        repository.insert(monitor);
        return monitor;
    }

    @Transactional(readOnly = true)
    public Monitor get(UUID id) { return required(id, false); }

    @Transactional
    public Monitor edit(UUID id, MonitorConfiguration configuration) {
        return change(id, monitor -> monitor.edit(configuration, now()));
    }

    @Transactional
    public Monitor pause(UUID id) { return change(id, monitor -> monitor.pause(now())); }

    @Transactional
    public Monitor resume(UUID id) { return change(id, monitor -> monitor.resume(now())); }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) { throw MonitorProblem.invalid(Map.of("page", "invalid pagination")); }
        long count = repository.count();
        return new Page(repository.list(page, size), page, size, count, (count + size - 1) / size);
    }

    public record Page(List<Monitor> items, int page, int size, long totalElements, long totalPages) { }

    private Monitor change(UUID id, UnaryOperator<Monitor> operation) {
        var before = required(id, true);
        var after = operation.apply(before);
        if (!before.equals(after)) { repository.save(after); }
        return after;
    }

    private Monitor required(UUID id, boolean lock) {
        return repository.find(id, lock).orElseThrow(() -> new MonitorProblem(404, "MONITOR_NOT_FOUND", Map.of()));
    }
}
