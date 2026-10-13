package br.com.monitor.administration;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitors")
public class MonitorController {
    private static final Set<String> FIELDS = Set.of("name", "url", "intervalSeconds", "timeoutSeconds",
            "expectedStatus", "failureThreshold", "recoveryThreshold", "alertRecipient");
    private final MonitorService service;

    public MonitorController(MonitorService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<View> create(@RequestBody JsonNode body) {
        var monitor = service.create(configuration(body));
        return ResponseEntity.created(URI.create("/api/admin/monitors/" + monitor.id())).body(view(monitor));
    }

    @GetMapping("/{id}")
    public View get(@PathVariable String id) { return view(service.get(identifier(id))); }

    private UUID identifier(String id) {
        try {
            var uuid = UUID.fromString(id);
            if (!uuid.toString().equalsIgnoreCase(id)) { throw new IllegalArgumentException(); }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw MonitorProblem.invalid(Map.of("id", "must be a UUID"));
        }
    }

    private MonitorConfiguration configuration(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw MonitorProblem.invalid(Map.of("body", "must be a JSON object"));
        }
        var errors = new LinkedHashMap<String, String>();
        body.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field)) { errors.put(field, "unknown field"); }
        });
        var name = text(body, "name", errors);
        var url = text(body, "url", errors);
        var interval = integer(body, "intervalSeconds", errors);
        var timeout = integer(body, "timeoutSeconds", errors);
        var status = integer(body, "expectedStatus", errors);
        var failures = integer(body, "failureThreshold", errors);
        var recoveries = integer(body, "recoveryThreshold", errors);
        var recipient = text(body, "alertRecipient", errors);
        MonitorConfiguration result = null;
        try {
            result = MonitorConfiguration.validated(name, url, interval, timeout, status, failures, recoveries,
                    recipient);
        } catch (MonitorProblem problem) {
            problem.errors().forEach(errors::putIfAbsent);
        }
        if (!errors.isEmpty()) { throw MonitorProblem.invalid(errors); }
        return result;
    }

    private String text(JsonNode body, String field, Map<String, String> errors) {
        var value = body.get(field);
        if (value == null || value.isNull()) { return null; }
        if (!value.isTextual()) { errors.put(field, "must be a string"); return null; }
        return value.textValue();
    }

    private Integer integer(JsonNode body, String field, Map<String, String> errors) {
        var value = body.get(field);
        if (value == null || value.isNull()) { return null; }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            errors.put(field, "must be an integer");
            return null;
        }
        return value.intValue();
    }

    private View view(Monitor monitor) {
        var s = monitor.state();
        var evidence = monitor.evidence(service.now());
        return new View(monitor.id(), monitor.configuration(), s.lifecycle(), s.confirmedState(), evidence,
                "FRESH".equals(evidence), s.lastObservedAt(), s.openIncidentId(), s.failureCount(), s.recoveryCount(),
                s.configurationVersion(), s.executionGeneration(), s.nextCheckAt(), monitor.createdAt(),
                monitor.updatedAt());
    }

    public record View(UUID id, @JsonUnwrapped MonitorConfiguration configuration, String lifecycle,
            String confirmedState, String evidence, boolean recentObservation, Instant lastObservedAt,
            UUID openIncidentId, int failureCount, int recoveryCount, long configurationVersion,
            long executionGeneration, Instant nextCheckAt, Instant createdAt, Instant updatedAt) { }
}
