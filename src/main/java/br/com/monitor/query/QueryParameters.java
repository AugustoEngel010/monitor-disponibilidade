package br.com.monitor.query;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.DateTimeException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import br.com.monitor.administration.MonitorProblem;
import br.com.monitor.monitoring.CheckResult.Outcome;
import org.springframework.util.MultiValueMap;

public record QueryParameters(Instant from, Instant to, int page, int size,
        String outcome, String status, String type) {
    public long offset() { return (long) page * size; }

    public static QueryParameters parse(MultiValueMap<String, String> values, String resource, Instant now) {
        var errors = new LinkedHashMap<String, String>();
        var allowed = switch (resource) {
            case "checks" -> Set.of("from", "to", "page", "size", "outcome");
            case "incidents" -> Set.of("from", "to", "page", "size", "status");
            case "notifications" -> Set.of("from", "to", "page", "size", "status", "type");
            default -> Set.of("from", "to");
        };
        values.forEach((key, entries) -> {
            if (!allowed.contains(key)) { errors.put(key, "unknown parameter"); }
            else if (entries.size() != 1) { errors.put(key, "must occur once"); }
        });
        int page = number(values.getFirst("page"), "page", 0, 0, Integer.MAX_VALUE, errors);
        int size = number(values.getFirst("size"), "size", 20, 1, 100, errors);
        Instant to = instant(values.getFirst("to"), "to", now, errors);
        Instant from = instant(values.getFirst("from"), "from", to.minusSeconds(86400), errors);
        if (!from.isBefore(to)) { errors.putIfAbsent("from", "must precede to"); }
        if (to.isAfter(now)) { errors.putIfAbsent("to", "must not be in the future"); }
        String outcome = values.getFirst("outcome");
        if (outcome != null) {
            try { Outcome.valueOf(outcome); }
            catch (IllegalArgumentException exception) { errors.putIfAbsent("outcome", "must be a check outcome"); }
        }
        String status = values.getFirst("status");
        Set<String> statuses = "incidents".equals(resource) ? Set.of("OPEN", "CLOSED")
                : Set.of("PENDING", "SENDING", "ACCEPTED", "EXHAUSTED");
        if (status != null && !statuses.contains(status)) { errors.putIfAbsent("status", "must be a resource status"); }
        String type = values.getFirst("type");
        if (type != null && !Set.of("DOWN", "RECOVERY").contains(type)) {
            errors.putIfAbsent("type", "must be DOWN or RECOVERY");
        }
        if (!errors.isEmpty()) { throw MonitorProblem.invalid(errors); }
        return new QueryParameters(from, to, page, size, outcome, status, type);
    }

    public static UUID identifier(String id) {
        try {
            UUID value = UUID.fromString(id);
            if (!value.toString().equalsIgnoreCase(id)) { throw new IllegalArgumentException(); }
            return value;
        } catch (IllegalArgumentException exception) {
            throw MonitorProblem.invalid(Map.of("id", "must be a UUID"));
        }
    }

    private static int number(String input, String field, int fallback, int min, int max,
            Map<String, String> errors) {
        if (input == null) { return fallback; }
        try {
            if (!input.matches("[0-9]+")) { throw new NumberFormatException(); }
            int value = Integer.parseInt(input);
            if (value < min || value > max) { throw new NumberFormatException(); }
            return value;
        } catch (NumberFormatException exception) {
            errors.putIfAbsent(field, "must be an integer between " + min + " and " + max);
            return fallback;
        }
    }

    private static Instant instant(String input, String field, Instant fallback, Map<String, String> errors) {
        if (input == null) { return fallback; }
        try {
            Instant value = OffsetDateTime.parse(input).toInstant();
            // Bound arithmetic and JDBC timestamps to the supported product calendar.
            if (value.isBefore(Instant.parse("0001-01-02T00:00:00Z"))
                    || value.isAfter(Instant.parse("9999-12-30T00:00:00Z")) || value.getNano() % 1000 != 0) {
                throw new DateTimeException("Invalid instant");
            }
            return value;
        } catch (DateTimeException exception) {
            errors.putIfAbsent(field, "must be an ISO-8601 timestamp with offset and at most microsecond precision");
            return fallback;
        }
    }
}
