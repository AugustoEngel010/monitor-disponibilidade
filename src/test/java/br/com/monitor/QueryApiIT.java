package br.com.monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;

class QueryApiIT extends QueryFixture {
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String credentials = "Basic " + Base64.getEncoder().encodeToString(
            (UUID.randomUUID() + ":" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
    private ServletWebServerApplicationContext context;
    private String address;

    @BeforeAll
    void startHttp() {
        var pair = new String(Base64.getDecoder().decode(credentials.substring(6)), StandardCharsets.UTF_8)
                .split(":");
        context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(
                MonitorApplication.class, FixedClock.class).run("--server.port=0",
                        "--spring.datasource.url=" + db.getJdbcUrl(),
                        "--spring.datasource.username=" + db.getUsername(),
                        "--spring.datasource.password=" + db.getPassword(),
                        "--ADMIN_USERNAME=" + pair[0], "--ADMIN_PASSWORD=" + pair[1],
                        "--monitor.scheduler.enabled=false", "--monitor.notifications.enabled=false");
        address = "http://localhost:" + context.getWebServer().getPort();
    }

    @AfterAll
    void stopHttp() { context.close(); }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock queryClock() { return Clock.fixed(Instant.parse("2026-10-18T12:10:00Z"), ZoneOffset.UTC); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"checks", "incidents", "notifications", "summary"})
    void everyRouteProtectsEmptyMissingAndMalformedIdentifiers(String resource) throws Exception {
        String path = path(monitor.id().toString(), resource);
        var emptyResponse = get(path, true);
        assertThat(emptyResponse.statusCode()).isEqualTo(200);
        assertThat(emptyResponse.headers().firstValue("cache-control").orElse("")).contains("no-store");
        var body = tree(emptyResponse);
        if ("summary".equals(resource)) {
            assertThat(body.path("label").asText()).isEqualTo("SEM_DADOS");
            assertThat(body.path("statistics").path("successRatePercent").isNull()).isTrue();
            assertThat(body.path("window").path("from").asText()).isEqualTo("2026-10-17T12:10:00Z");
            assertThat(body.path("window").path("to").asText()).isEqualTo("2026-10-18T12:10:00Z");
        } else {
            assertThat(body.path("items").size()).isZero();
            assertThat(body.path("page").asInt()).isZero();
            assertThat(body.path("size").asInt()).isEqualTo(20);
            assertThat(body.path("totalElements").asInt()).isZero();
            assertThat(body.path("totalPages").asInt()).isZero();
        }
        var missing = get(path(UUID.randomUUID().toString(), resource), true);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(tree(missing).path("code").asText()).isEqualTo("MONITOR_NOT_FOUND");
        assertThat(tree(missing).path("errors").size()).isZero();
        var malformed = get(path("invalid", resource), true);
        assertThat(malformed.statusCode()).isEqualTo(400);
        assertThat(tree(malformed).path("errors").path("id").asText()).isEqualTo("must be a UUID");
        var forbidden = get(path, false);
        assertThat(forbidden.statusCode()).isEqualTo(401);
        assertThat(forbidden.body()).isEmpty();
        assertThat(forbidden.headers().firstValue("www-authenticate").orElse("")).contains("Basic");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.check_results", Integer.class)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"checks,page=-1,page", "checks,size=101,size", "checks,outcome=BAD,outcome",
        "checks,page=0&page=1,page", "checks,from=2026-10-18T12:10:00Z,from",
        "checks,to=2026-10-18T12:10:01Z,to", "checks,from=2026-10-18T12:00:00,from",
        "checks,unknown=value,unknown", "checks,status=OPEN,status", "incidents,status=PENDING,status",
        "incidents,size=0,size", "incidents,to=bad,to", "incidents,outcome=SUCCESS,outcome",
        "notifications,status=OPEN,status", "notifications,type=BAD,type", "notifications,page=1.5,page",
        "notifications,from=bad,from", "summary,from=bad,from", "summary,page=0,page",
        "summary,to=2026-10-18T12:10:01Z,to", "summary,to=bad&to=bad,to"})
    void invalidFiltersReturnConventionalFixedFieldErrors(String resource, String query, String field)
            throws Exception {
        var response = get(path(monitor.id().toString(), resource) + "?" + query, true);
        assertThat(response.statusCode()).isEqualTo(400);
        var body = tree(response);
        assertThat(body.path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.path("errors").path(field).asText()).isNotBlank();
        assertThat(body.toString()).doesNotContain(credentials, "value");
    }

    @Test
    void allFourContractsSerializeRealEvidenceAndSupportWindowFiltersAndEmptyPages() throws Exception {
        processor.process(check(monitor, 1, 500, Outcome.HTTP_FAILURE, 100_000_000, false).executionId());
        String root = "/api/admin/monitors/" + monitor.id();
        String window = "from=2026-10-18T12:00:00Z&to=2026-10-18T12:10:00Z";
        var checksResponse = get(root + "/checks?" + window + "&outcome=HTTP_FAILURE&size=1", true);
        assertThat(checksResponse.statusCode()).isEqualTo(200);
        var check = tree(checksResponse).path("items").get(0);
        assertThat(check.path("executionId").asText()).isEqualTo(uuid(1).toString());
        assertThat(check.path("finishedAt").asText()).isEqualTo("2026-10-18T12:08:20Z");
        assertThat(check.path("outcome").asText()).isEqualTo("HTTP_FAILURE");
        assertThat(check.path("errorMessage").asText()).isEqualTo("Unexpected HTTP status");
        var incidentResponse = get(root + "/incidents?" + window + "&status=OPEN", true);
        assertThat(incidentResponse.statusCode()).isEqualTo(200);
        var incident = tree(incidentResponse).path("items").get(0);
        assertThat(incident.path("status").asText()).isEqualTo("OPEN");
        assertThat(incident.path("ageSeconds").decimalValue()).isEqualByComparingTo("100");
        assertThat(incident.path("estimatedDurationSeconds").isNull()).isTrue();
        var notifications = get(root + "/notifications?" + window + "&status=PENDING&type=DOWN", true);
        assertThat(notifications.statusCode()).isEqualTo(200);
        var notification = tree(notifications).path("items").get(0);
        assertThat(notification.path("incidentId")).isEqualTo(incident.path("id"));
        assertThat(notification.path("type").asText()).isEqualTo("DOWN");
        assertThat(notification.path("attempts").size()).isZero();
        var summaryResponse = get(root + "/summary?" + window, true);
        assertThat(summaryResponse.statusCode()).isEqualTo(200);
        var summary = tree(summaryResponse);
        assertThat(summary.path("label").asText()).isEqualTo("DOWN");
        assertThat(summary.path("evidence").asText()).isEqualTo("FRESH");
        assertThat(summary.path("currentIncident").path("id")).isEqualTo(incident.path("id"));
        assertThat(summary.path("lastObservation").path("executionId")).isEqualTo(check.path("executionId"));
        assertThat(summary.path("statistics").path("successRatePercent").decimalValue()).isEqualByComparingTo("0");
        assertThat(summary.path("statistics").path("averageResponseTimeMs").isNull()).isTrue();
        for (var resource : new String[] {"checks", "incidents", "notifications"}) {
            var beyond = get(root + "/" + resource + "?" + window + "&page=2147483647&size=100", true);
            assertThat(beyond.statusCode()).isEqualTo(200);
            assertThat(tree(beyond).path("items").size()).isZero();
            assertThat(tree(beyond).path("totalElements").asLong()).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.check_results", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM monitor.incidents", Integer.class)).isEqualTo(1);
    }

    private String path(String id, String resource) { return "/api/admin/monitors/" + id + "/" + resource; }

    private HttpResponse<String> get(String path, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(address + path));
        if (authenticated) { request.header("Authorization", credentials); }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode tree(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
}
