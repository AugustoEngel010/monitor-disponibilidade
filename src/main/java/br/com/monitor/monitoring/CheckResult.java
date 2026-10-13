package br.com.monitor.monitoring;

import java.time.Instant;
import java.util.UUID;

public record CheckResult(UUID monitorId, UUID executionId, long configurationVersion, long executionGeneration,
        Instant startedAt, Instant finishedAt, long durationNanos, Outcome outcome, Integer httpStatus) {
    public String errorMessage() { return outcome.message(); }

    public enum Outcome {
        SUCCESS(true, null), HTTP_FAILURE(true, "Unexpected HTTP status"), TIMEOUT(true, "Attempt timed out"),
        DNS_FAILURE(true, "DNS resolution failed"), CONNECTION_FAILURE(true, "Connection failed"),
        TLS_FAILURE(true, "TLS validation or handshake failed"), INTERNAL_ERROR(false, "Internal execution error"),
        CANCELLED(false, "Attempt cancelled"), DESTINATION_BLOCKED(false, "Destination blocked by policy");

        private final boolean external;
        private final String message;

        Outcome(boolean external, String message) { this.external = external; this.message = message; }
        public boolean external() { return external; }
        public String message() { return message; }
    }
}
