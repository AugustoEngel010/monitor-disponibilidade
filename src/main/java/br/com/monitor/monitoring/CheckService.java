package br.com.monitor.monitoring;

import java.util.UUID;
import br.com.monitor.administration.Monitor;
import org.springframework.stereotype.Service;

@Service
public class CheckService {
    private final HttpCheckExecutor executor;
    private final CheckRepository repository;

    public CheckService(HttpCheckExecutor executor, CheckRepository repository) {
        this.executor = executor;
        this.repository = repository;
    }

    public CheckResult executeAndRecord(Monitor snapshot, UUID executionId) {
        var result = executor.execute(snapshot, executionId);
        // JDBC must finish recording a cancellation; preserve caller interruption even if persistence fails.
        boolean interrupted = Thread.interrupted();
        try { return repository.save(result); }
        finally { if (interrupted) { Thread.currentThread().interrupt(); } }
    }
}
