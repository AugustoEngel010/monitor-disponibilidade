package br.com.monitor.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationDelivery(UUID id, int attempt, UUID incidentId, String type, String recipient,
        String monitorName, String monitorUrl, Instant firstFailureAt, Instant downConfirmedAt,
        Instant recoveryStartedAt, Instant recoveredAt) {
    public String messageId() { return "<notification-" + id + "@monitor.local>"; }
}
