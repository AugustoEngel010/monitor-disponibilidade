package br.com.monitor.operation;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class OperationalEvents {
    private static final Logger LOG = LoggerFactory.getLogger(OperationalEvents.class);

    public enum Event { CHECK_RECORDED, INCIDENT_TRANSITION, NOTIFICATION_CLAIMED, NOTIFICATION_FINISHED }

    private OperationalEvents() { }

    public static void committed(Event event, UUID monitorId, UUID executionId, UUID incidentId,
            UUID notificationId, Enum<?> outcome) {
        Runnable emit = () -> LOG.atInfo().addKeyValue("event", event.name())
                .addKeyValue("monitorId", monitorId).addKeyValue("executionId", executionId)
                .addKeyValue("incidentId", incidentId).addKeyValue("notificationId", notificationId)
                .addKeyValue("outcome", outcome == null ? null : outcome.name()).log("Operation committed");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { emit.run(); }
            });
        } else { emit.run(); }
    }
}
