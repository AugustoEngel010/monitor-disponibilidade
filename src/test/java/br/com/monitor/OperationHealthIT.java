package br.com.monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.operation.OperationalEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class OperationHealthIT {
    @Test
    void databaseFailureAffectsReadinessOnlyAndMetricsRequireBasic(CapturedOutput output) throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            db.start();
            try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(
                    MonitorApplication.class)
                    .run("--server.port=0", "--spring.datasource.url=" + db.getJdbcUrl(),
                            "--spring.datasource.username=" + db.getUsername(),
                            "--spring.datasource.password=" + db.getPassword(),
                            "--ADMIN_USERNAME=operation-test", "--ADMIN_PASSWORD=operation-test-only",
                            "--monitor.scheduler.enabled=false", "--monitor.notifications.enabled=false")) {
                var client = HttpClient.newHttpClient();
                String address = "http://localhost:" + context.getWebServer().getPort();
                assertThat(get(client, address, "/actuator/metrics", false).statusCode()).isEqualTo(401);
                assertThat(get(client, address, "/actuator/metrics/monitor.notifications", true).statusCode())
                        .isEqualTo(200);
                assertThat(get(client, address, "/actuator/health/readiness", false).body())
                        .isEqualTo("{\"status\":\"UP\"}");
                UUID monitor = UUID.randomUUID();
                UUID execution = UUID.randomUUID();
                UUID incident = UUID.randomUUID();
                UUID notification = UUID.randomUUID();
                OperationalEvents.committed(OperationalEvents.Event.CHECK_RECORDED, monitor, execution,
                        incident, notification, CheckResult.Outcome.INTERNAL_ERROR);
                var jsonLine = output.getOut().lines().filter(line -> line.contains("Operation committed"))
                        .reduce((left, right) -> right).orElseThrow();
                var json = new ObjectMapper().readTree(jsonLine);
                assertThat(json.path("monitorId").asText()).isEqualTo(monitor.toString());
                assertThat(json.path("executionId").asText()).isEqualTo(execution.toString());
                assertThat(json.path("incidentId").asText()).isEqualTo(incident.toString());
                assertThat(json.path("notificationId").asText()).isEqualTo(notification.toString());
                assertThat(json.path("outcome").asText()).isEqualTo("INTERNAL_ERROR");
                assertThat(jsonLine).doesNotContain("operation-test-only", "Authorization", "stack_trace");
                db.stop();
                var readiness = get(client, address, "/actuator/health/readiness", false);
                assertThat(readiness.statusCode()).isEqualTo(503);
                assertThat(readiness.body()).isEqualTo("{\"status\":\"DOWN\"}");
                var liveness = get(client, address, "/actuator/health/liveness", false);
                assertThat(liveness.statusCode()).isEqualTo(200);
                assertThat(liveness.body()).isEqualTo("{\"status\":\"UP\"}");
            }
        }
    }

    private HttpResponse<String> get(HttpClient client, String address, String path, boolean authenticated)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(address + path)).timeout(java.time.Duration.ofSeconds(10));
        if (authenticated) {
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    "operation-test:operation-test-only".getBytes(StandardCharsets.UTF_8)));
        }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
