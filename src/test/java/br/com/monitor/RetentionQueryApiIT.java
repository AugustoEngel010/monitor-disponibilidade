package br.com.monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Base64;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.operation.RetentionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RetentionQueryApiIT extends QueryFixture {
    private ServletWebServerApplicationContext context;

    @BeforeAll
    void openApi() {
        context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(MonitorApplication.class)
                .run("--server.port=0", "--spring.datasource.url=" + db.getJdbcUrl(),
                        "--spring.datasource.username=" + db.getUsername(),
                        "--spring.datasource.password=" + db.getPassword(),
                        "--ADMIN_USERNAME=retention-test", "--ADMIN_PASSWORD=retention-test-only",
                        "--monitor.scheduler.enabled=false", "--monitor.notifications.enabled=false",
                        "--monitor.retention.enabled=false");
    }

    @AfterAll
    void closeApi() { if (context != null) { context.close(); } }

    @Test
    void allFourResourcesSerializeRetentionAndRemovedWindowsNeverReportSuccess() throws Exception {
        var old = check(monitor, 1, -10, CheckResult.Outcome.HTTP_FAILURE, 0, false);
        processor.process(old.executionId());
        new RetentionService(named, new DataSourceTransactionManager(jdbc.getDataSource()),
                Clock.fixed(base.plusSeconds(86400), ZoneOffset.UTC),
                new MockEnvironment().withProperty("monitor.retention.days", "1")).clean();
        var client = HttpClient.newHttpClient();
        for (String resource : new String[] {"summary", "checks", "incidents", "notifications"}) {
            String url = "http://localhost:" + context.getWebServer().getPort() + "/api/admin/monitors/"
                    + monitor.id() + "/" + resource + "?from=" + base.minusSeconds(60) + "&to=" + base.minusSeconds(1);
            var request = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(
                            "retention-test:retention-test-only".getBytes(StandardCharsets.UTF_8))).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var json = new ObjectMapper().readTree(response.body());
            assertThat(json.path("historyAvailableFrom").asText()).isEqualTo(base.toString());
            assertThat(json.path("retainedOutPeriods").size()).isEqualTo(1);
            assertThat(json.at("/retainedOutPeriods/0/from").asText()).isEqualTo(base.minusSeconds(60).toString());
            assertThat(json.at("/retainedOutPeriods/0/to").asText()).isEqualTo(base.minusSeconds(1).toString());
            if (resource.equals("summary")) {
                assertThat(json.at("/statistics/dataStatus").asText()).isEqualTo("NO_DATA");
                assertThat(json.at("/statistics/successRatePercent").isNull()).isTrue();
                assertThat(json.at("/statistics/validSamples").asLong()).isZero();
                assertThat(json.path("noObservationPeriods").size()).isZero();
            } else if (resource.equals("checks")) {
                assertThat(json.path("items").size()).isZero();
                assertThat(json.path("totalElements").asLong()).isZero();
            } else {
                assertThat(json.path("items").size()).isEqualTo(1);
            }
        }
    }
}
