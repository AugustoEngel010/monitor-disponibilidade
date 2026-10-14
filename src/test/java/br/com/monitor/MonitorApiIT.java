package br.com.monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonitorApiIT {
    private static final String ROOT = "/api/admin/monitors";
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final String username = UUID.randomUUID().toString();
    private final String password = UUID.randomUUID().toString();
    private ServletWebServerApplicationContext context;
    private JdbcTemplate jdbc;
    private String address;
    private String cookie;
    private String token;

    @BeforeAll
    void start() throws Exception {
        db.start();
        context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(MonitorApplication.class)
                .run("--server.port=0", "--monitor.scheduler.enabled=false",
                        "--spring.datasource.url=" + db.getJdbcUrl(),
                        "--spring.datasource.username=" + db.getUsername(),
                        "--spring.datasource.password=" + db.getPassword(),
                        "--ADMIN_USERNAME=" + username, "--ADMIN_PASSWORD=" + password);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE monitor.api_history_fixture (monitor_id uuid REFERENCES monitor.monitors(id),"
                + " payload text NOT NULL)");
        address = "http://localhost:" + context.getWebServer().getPort();
        var csrf = request("GET", "/api/admin/csrf", null, true, false);
        assertThat(csrf.statusCode()).isEqualTo(200);
        cookie = csrf.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        token = tree(csrf).path("token").asText();
        assertThat(token).isNotBlank();
    }

    @BeforeEach
    void clear() {
        jdbc.execute("TRUNCATE monitor.check_attempts, monitor.api_history_fixture, monitor.check_results,"
                + " monitor.monitors CASCADE");
    }

    @AfterAll
    void stop() { context.close(); db.stop(); }

    @Test
    void registrationReturnsLocationCompleteConfigurationAndUnobservedState() throws Exception {
        var response = send("POST", ROOT, valid());
        assertThat(response.statusCode()).isEqualTo(201);
        var monitor = tree(response);
        var id = UUID.fromString(monitor.path("id").asText());
        assertThat(response.headers().firstValue("location")).contains(ROOT + "/" + id);
        for (var fields = valid().fields(); fields.hasNext();) {
            var field = fields.next();
            assertThat(monitor.get(field.getKey())).isEqualTo(field.getValue());
        }
        assertThat(monitor.path("lifecycle").asText()).isEqualTo("ACTIVE");
        assertThat(monitor.path("confirmedState").asText()).isEqualTo("UNKNOWN");
        assertThat(monitor.path("evidence").asText()).isEqualTo("NEVER_OBSERVED");
        assertThat(monitor.path("recentObservation").asBoolean()).isFalse();
        assertThat(monitor.path("failureCount").asInt()).isZero();
        assertThat(monitor.path("recoveryCount").asInt()).isZero();
        assertThat(monitor.path("configurationVersion").asLong()).isEqualTo(1);
        assertThat(monitor.path("executionGeneration").asLong()).isEqualTo(1);
        assertThat(monitor.path("lastObservedAt").isNull()).isTrue();
        assertThat(monitor.path("openIncidentId").isNull()).isTrue();
        assertThat(monitor.path("nextCheckAt")).isEqualTo(monitor.path("createdAt"));
        assertThat(monitor.path("updatedAt")).isEqualTo(monitor.path("createdAt"));
        assertThat(tree(send("GET", ROOT + "/" + id, null))).isEqualTo(monitor);
        assertThat(jdbc.queryForObject("SELECT name FROM monitor.monitors WHERE id=?", String.class, id))
                .isEqualTo("Demo");
    }

    @Test
    void omittedNumericFieldsUseDocumentedDefaultsAndNamesMayRepeat() throws Exception {
        var input = json.createObjectNode().put("name", " Demo ").put("url", " https://example.org/a ")
                .put("alertRecipient", " ops@example.org ");
        for (int i = 0; i < 2; i++) {
            var response = send("POST", ROOT, input);
            assertThat(response.statusCode()).isEqualTo(201);
            var monitor = tree(response);
            assertThat(monitor.path("name").asText()).isEqualTo("Demo");
            assertThat(monitor.path("url").asText()).isEqualTo("https://example.org/a");
            assertThat(monitor.path("alertRecipient").asText()).isEqualTo("ops@example.org");
            assertThat(monitor.path("intervalSeconds").asInt()).isEqualTo(60);
            assertThat(monitor.path("timeoutSeconds").asInt()).isEqualTo(5);
            assertThat(monitor.path("expectedStatus").asInt()).isEqualTo(200);
            assertThat(monitor.path("failureThreshold").asInt()).isEqualTo(3);
            assertThat(monitor.path("recoveryThreshold").asInt()).isEqualTo(2);
        }
        assertThat(count()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"intervalSeconds,9", "intervalSeconds,3601", "timeoutSeconds,0", "timeoutSeconds,31",
            "expectedStatus,199", "expectedStatus,600", "failureThreshold,0", "failureThreshold,11",
            "recoveryThreshold,0", "recoveryThreshold,11"})
    void invalidNumericBoundsReturnFieldErrorsWithoutPersisting(String field, int value) throws Exception {
        assertInvalid(send("POST", ROOT, valid().put(field, value)), field);
        assertThat(count()).isZero();
    }

    @Test
    void incompatibleTimeoutAndEveryInvalidConfigurationFieldAreReported() throws Exception {
        assertInvalid(send("POST", ROOT, valid().put("intervalSeconds", 10).put("timeoutSeconds", 10)),
                "timeoutSeconds");
        var response = send("POST", ROOT, json.createObjectNode().put("name", " ").put("url", "ftp://e.org")
                .put("intervalSeconds", 9).put("timeoutSeconds", 31).put("expectedStatus", 199)
                .put("failureThreshold", 0).put("recoveryThreshold", 11).put("alertRecipient", "invalid"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(tree(response).path("errors").size()).isEqualTo(8);
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://user:secret@example.org", "https://@example.org", "ftp://example.org",
            "https://example.org?q=secret", "https://example.org#fragment", "http://example.org:65536"})
    void invalidUrlIsRejectedWithoutEchoingItsContent(String url) throws Exception {
        var response = send("POST", ROOT, valid().put("url", url));
        assertInvalid(response, "url");
        assertThat(response.body()).doesNotContain(url, "secret");
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.5", "\"60\"", "true", "2147483648", "{}", "[]"})
    void numericCoercionIsNotAccepted(String raw) throws Exception {
        var input = valid();
        input.set("intervalSeconds", json.readTree(raw));
        assertInvalid(send("POST", ROOT, input), "intervalSeconds");
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "true", "{", ""})
    void malformedOrNonObjectJsonReturnsSanitizedBodyError(String body) throws Exception {
        assertInvalid(request("POST", ROOT, body, true, true), "body");
        assertThat(count()).isZero();
    }

    @Test
    void wrongTextTypesUnknownFieldsAndMissingRequiredFieldsReturnErrors() throws Exception {
        assertInvalid(send("POST", ROOT, valid().put("name", 123)), "name");
        assertInvalid(send("POST", ROOT, valid().put("unexpected", "sensitive")), "unexpected");
        for (String field : new String[] {"name", "url", "alertRecipient"}) {
            var input = valid();
            input.remove(field);
            assertInvalid(send("POST", ROOT, input), field);
        }
        assertInvalid(send("POST", ROOT, valid().put("alertRecipient", "a@e.org,b@e.org")), "alertRecipient");
        assertThat(count()).isZero();
    }

    @Test
    void readsAndCreationRequireBasicAndWritesRequireSessionCsrf() throws Exception {
        assertThat(request("GET", ROOT + "/" + UUID.randomUUID(), null, false, false).statusCode())
                .isEqualTo(401);
        assertThat(request("POST", ROOT, valid().toString(), false, false).statusCode()).isEqualTo(401);
        assertThat(request("POST", ROOT, valid().toString(), true, false).statusCode()).isEqualTo(403);
        assertThat(count()).isZero();
        assertThat(send("POST", ROOT, valid()).statusCode()).isEqualTo(201);
    }

    @Test
    void missingIdentifierReturns404AndMalformedIdentifierReturnsFieldError() throws Exception {
        var missing = send("GET", ROOT + "/" + UUID.randomUUID(), null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(tree(missing).path("code").asText()).isEqualTo("MONITOR_NOT_FOUND");
        assertInvalid(send("GET", ROOT + "/invalid", null), "id");
    }

    @Test
    void oneHundredAndFirstMonitorReturns409() throws Exception {
        var input = valid();
        for (int i = 0; i < 100; i++) { assertThat(send("POST", ROOT, input).statusCode()).isEqualTo(201); }
        var response = send("POST", ROOT, input);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(tree(response).path("code").asText()).isEqualTo("MONITOR_LIMIT");
        assertThat(count()).isEqualTo(100);
    }

    @Test
    void editingReplacesConfigurationAndResetsPolicyWithoutDeletingHistory() throws Exception {
        var original = create();
        var id = original.path("id").asText();
        var path = ROOT + "/" + id;
        jdbc.update("INSERT INTO monitor.api_history_fixture VALUES (?::uuid, 'original evidence')", id);
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='UP', failure_count=2, first_failure_at=now(),"
                + " last_observed_at=now(), current_observation=true WHERE id=?::uuid", id);
        var replacement = valid().put("name", "Changed").put("url", "http://other.org")
                .put("intervalSeconds", 10).put("timeoutSeconds", 9).put("expectedStatus", 599)
                .put("failureThreshold", 10).put("recoveryThreshold", 10).put("alertRecipient", "new@example.org");
        var before = tree(send("GET", path, null));
        var response = send("PUT", path, replacement);
        assertThat(response.statusCode()).isEqualTo(200);
        var changed = tree(response);
        for (var fields = replacement.fields(); fields.hasNext();) {
            var field = fields.next();
            assertThat(changed.get(field.getKey())).isEqualTo(field.getValue());
        }
        assertThat(changed.path("id")).isEqualTo(original.path("id"));
        assertThat(changed.path("createdAt")).isEqualTo(original.path("createdAt"));
        assertThat(changed.path("lastObservedAt")).isEqualTo(before.path("lastObservedAt"));
        assertThat(changed.path("confirmedState").asText()).isEqualTo("UNKNOWN");
        assertThat(changed.path("evidence").asText()).isEqualTo("NEVER_OBSERVED");
        assertThat(changed.path("failureCount").asInt()).isZero();
        assertThat(changed.path("recoveryCount").asInt()).isZero();
        assertThat(changed.path("configurationVersion").asLong()).isEqualTo(2);
        assertThat(changed.path("executionGeneration").asLong()).isEqualTo(2);
        assertThat(changed.path("nextCheckAt")).isEqualTo(changed.path("updatedAt"));
        assertThat(tree(send("GET", path, null))).isEqualTo(changed);
        assertThat(tree(send("PUT", path, replacement))).isEqualTo(changed);
        assertHistory();
    }

    @Test
    void editingWhilePausedDoesNotScheduleAProbe() throws Exception {
        var id = create().path("id").asText();
        var path = ROOT + "/" + id;
        assertThat(send("POST", path + "/pause", null).statusCode()).isEqualTo(200);
        var response = send("PUT", path, valid().put("timeoutSeconds", 6));
        assertThat(response.statusCode()).isEqualTo(200);
        var changed = tree(response);
        assertThat(changed.path("lifecycle").asText()).isEqualTo("PAUSED");
        assertThat(changed.path("nextCheckAt").isNull()).isTrue();
        assertThat(changed.path("configurationVersion").asLong()).isEqualTo(2);
        assertThat(changed.path("executionGeneration").asLong()).isEqualTo(3);
    }

    @Test
    void incidentAllowsNameAndRecipientButRejectsEveryPolicyChangeAndMixedPutAtomically() throws Exception {
        var id = create().path("id").asText();
        var path = ROOT + "/" + id;
        var incident = UUID.randomUUID();
        IncidentFixtures.open(jdbc, UUID.fromString(id), incident, 1);
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='DOWN', open_incident_id=?, recovery_count=1,"
                + " recovery_started_at=now(), last_observed_at=now(), current_observation=true WHERE id=?::uuid",
                incident, id);
        jdbc.update("INSERT INTO monitor.api_history_fixture VALUES (?::uuid, 'original evidence')", id);
        var before = tree(send("GET", path, null));
        ObjectNode[] rejected = {valid().put("url", "http://other.org").put("name", "mixed"),
            valid().put("intervalSeconds", 61), valid().put("timeoutSeconds", 6), valid().put("expectedStatus", 201),
            valid().put("failureThreshold", 4), valid().put("recoveryThreshold", 3)};
        for (var input : rejected) {
            var response = send("PUT", path, input);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(tree(response).path("code").asText()).isEqualTo("OPEN_INCIDENT");
            assertThat(tree(send("GET", path, null))).isEqualTo(before);
        }
        var response = send("PUT", path, valid().put("name", "New name").put("alertRecipient", "new@example.org"));
        assertThat(response.statusCode()).isEqualTo(200);
        var changed = tree(response);
        assertThat(changed.path("name").asText()).isEqualTo("New name");
        assertThat(changed.path("alertRecipient").asText()).isEqualTo("new@example.org");
        for (String field : new String[] {"confirmedState", "openIncidentId", "recoveryCount", "configurationVersion",
                "executionGeneration", "lastObservedAt", "nextCheckAt", "evidence", "failureCount"}) {
            assertThat(changed.path(field)).isEqualTo(before.path(field));
        }
        assertHistory();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "UP", "DOWN"})
    void repeatedPauseAndResumePreserveHealthHistoryAndRequireNewEvidence(String state) throws Exception {
        var original = create();
        var id = original.path("id").asText();
        var path = ROOT + "/" + id;
        var incident = "DOWN".equals(state) ? UUID.randomUUID() : null;
        IncidentFixtures.open(jdbc, UUID.fromString(id), incident, 1);
        jdbc.update("UPDATE monitor.monitors SET confirmed_state=?, open_incident_id=?, recovery_count=1,"
                + " recovery_started_at=now(), last_observed_at=now(), current_observation=true WHERE id=?::uuid",
                state, incident, id);
        jdbc.update("INSERT INTO monitor.api_history_fixture VALUES (?::uuid, 'original evidence')", id);
        var before = tree(send("GET", path, null));
        var pausedResponse = send("POST", path + "/pause", null);
        assertThat(pausedResponse.statusCode()).isEqualTo(200);
        var paused = tree(pausedResponse);
        assertThat(paused.path("lifecycle").asText()).isEqualTo("PAUSED");
        assertThat(paused.path("confirmedState").asText()).isEqualTo(state);
        assertThat(paused.path("openIncidentId")).isEqualTo(before.path("openIncidentId"));
        assertThat(paused.path("lastObservedAt")).isEqualTo(before.path("lastObservedAt"));
        assertThat(paused.path("failureCount").asInt()).isZero();
        assertThat(paused.path("recoveryCount").asInt()).isZero();
        assertThat(paused.path("configurationVersion").asLong()).isEqualTo(1);
        assertThat(paused.path("executionGeneration").asLong()).isEqualTo(2);
        assertThat(paused.path("nextCheckAt").isNull()).isTrue();
        assertThat(paused.path("evidence").asText()).isEqualTo("NEVER_OBSERVED");
        assertThat(paused.path("recentObservation").asBoolean()).isFalse();
        assertThat(tree(send("POST", path + "/pause", null))).isEqualTo(paused);
        var resumeResponse = send("POST", path + "/resume", null);
        assertThat(resumeResponse.statusCode()).isEqualTo(200);
        var resumed = tree(resumeResponse);
        assertThat(resumed.path("lifecycle").asText()).isEqualTo("ACTIVE");
        assertThat(resumed.path("confirmedState").asText()).isEqualTo(state);
        assertThat(resumed.path("openIncidentId")).isEqualTo(before.path("openIncidentId"));
        assertThat(resumed.path("lastObservedAt")).isEqualTo(before.path("lastObservedAt"));
        assertThat(resumed.path("failureCount").asInt()).isZero();
        assertThat(resumed.path("recoveryCount").asInt()).isZero();
        assertThat(resumed.path("configurationVersion").asLong()).isEqualTo(1);
        assertThat(resumed.path("executionGeneration").asLong()).isEqualTo(3);
        assertThat(resumed.path("nextCheckAt")).isEqualTo(resumed.path("updatedAt"));
        assertThat(resumed.path("evidence").asText()).isEqualTo("NEVER_OBSERVED");
        assertThat(resumed.path("recentObservation").asBoolean()).isFalse();
        assertThat(tree(send("POST", path + "/resume", null))).isEqualTo(resumed);
        assertThat(tree(send("GET", path, null))).isEqualTo(resumed);
        assertHistory();
    }

    @Test
    void alreadyActiveResumePreservesAllFields() throws Exception {
        var original = create();
        var response = send("POST", ROOT + "/" + original.path("id").asText() + "/resume", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(tree(response)).isEqualTo(original);
    }

    @ParameterizedTest
    @CsvSource({"PUT,''", "POST,/pause", "POST,/resume"})
    void everyMutationProtectsStateAndReturns404ForMissingIdentifiers(String method, String suffix) throws Exception {
        var id = create().path("id").asText();
        var path = ROOT + "/" + id + suffix;
        var snapshot = jdbc.queryForList("SELECT * FROM monitor.monitors");
        assertThat(request(method, path, valid().toString(), false, false).statusCode()).isEqualTo(401);
        assertThat(request(method, path, valid().toString(), true, false).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForList("SELECT * FROM monitor.monitors")).isEqualTo(snapshot);
        var missing = send(method, ROOT + "/" + UUID.randomUUID() + suffix, valid());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(tree(missing).path("code").asText()).isEqualTo("MONITOR_NOT_FOUND");
        assertInvalid(send(method, ROOT + "/invalid" + suffix, valid()), "id");
    }

    @Test
    void invalidPutDoesNotChangeAnyPersistedField() throws Exception {
        var id = create().path("id").asText();
        var snapshot = jdbc.queryForList("SELECT * FROM monitor.monitors");
        var path = ROOT + "/" + id;
        for (String field : new String[] {"intervalSeconds", "timeoutSeconds", "expectedStatus",
                "failureThreshold", "recoveryThreshold"}) {
            assertInvalid(send("PUT", path, valid().put(field, 0)), field);
            assertInvalid(send("PUT", path, valid().put(field, "1")), field);
        }
        assertInvalid(send("PUT", path, valid().put("url", "http://user:secret@example.org")), "url");
        assertInvalid(send("PUT", path, valid().put("name", " ")), "name");
        assertInvalid(send("PUT", path, valid().put("alertRecipient", "invalid")), "alertRecipient");
        assertInvalid(request("PUT", path, "{", true, true), "body");
        assertThat(jdbc.queryForList("SELECT * FROM monitor.monitors")).isEqualTo(snapshot);
    }

    @Test
    void paginationDefaultsEmptyAndOutOfRangePagesAreExplicit() throws Exception {
        var response = send("GET", ROOT, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var empty = tree(response);
        assertThat(empty.path("items").size()).isZero();
        assertThat(empty.path("page").asInt()).isZero();
        assertThat(empty.path("size").asInt()).isEqualTo(20);
        assertThat(empty.path("totalElements").asLong()).isZero();
        assertThat(empty.path("totalPages").asLong()).isZero();
        create();
        var beyond = tree(send("GET", ROOT + "?page=2147483647&size=100", null));
        assertThat(beyond.path("items").size()).isZero();
        assertThat(beyond.path("page").asInt()).isEqualTo(2147483647);
        assertThat(beyond.path("size").asInt()).isEqualTo(100);
        assertThat(beyond.path("totalElements").asLong()).isEqualTo(1);
        assertThat(beyond.path("totalPages").asLong()).isEqualTo(1);
    }

    @Test
    void adjacentPagesAreOrderedByCreationAndUuidWithoutOverlap() throws Exception {
        var ids = new ArrayList<String>();
        for (int i = 0; i < 5; i++) { ids.add(create().path("id").asText()); }
        jdbc.update("UPDATE monitor.monitors SET created_at='2026-10-13T12:00:00Z'");
        var sorted = ids.stream().sorted().toList();
        // The smallest UUID is deliberately newer, proving created_at is the primary key.
        jdbc.update("UPDATE monitor.monitors SET created_at='2026-10-13T13:00:00Z' WHERE id=?::uuid", sorted.get(0));
        var expected = new ArrayList<>(sorted.subList(1, 5));
        expected.add(sorted.get(0));
        var actual = new ArrayList<String>();
        for (int page = 0; page < 3; page++) {
            var response = send("GET", ROOT + "?page=" + page + "&size=2", null);
            assertThat(response.statusCode()).isEqualTo(200);
            var body = tree(response);
            assertThat(body.path("page").asInt()).isEqualTo(page);
            assertThat(body.path("size").asInt()).isEqualTo(2);
            assertThat(body.path("totalElements").asLong()).isEqualTo(5);
            assertThat(body.path("totalPages").asLong()).isEqualTo(3);
            assertThat(body.path("items").size()).isEqualTo(page == 2 ? 1 : 2);
            for (var item : body.path("items")) { actual.add(item.path("id").asText()); }
            assertThat(tree(send("GET", ROOT + "?page=" + page + "&size=2", null))).isEqualTo(body);
        }
        assertThat(actual).containsExactlyElementsOf(expected);
        assertThat(actual).doesNotHaveDuplicates();
        var after = tree(send("GET", ROOT + "?page=3&size=2", null));
        assertThat(after.path("items").size()).isZero();
        assertThat(after.path("totalElements").asInt()).isEqualTo(5);
        assertThat(after.path("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    void listingSeparatesLifecycleHealthAndObservationFreshness() throws Exception {
        var never = create().path("id").asText();
        var fresh = create().path("id").asText();
        var stale = create().path("id").asText();
        var paused = create().path("id").asText();
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='UP', last_observed_at=now(),"
                + " current_observation=true WHERE id=?::uuid", fresh);
        var staleIncident = UUID.randomUUID();
        IncidentFixtures.open(jdbc, UUID.fromString(stale), staleIncident, 1);
        jdbc.update("UPDATE monitor.monitors SET confirmed_state='DOWN', open_incident_id=?,"
                + " last_observed_at=now()-interval '126 seconds', current_observation=true WHERE id=?::uuid",
                staleIncident, stale);
        assertThat(send("POST", ROOT + "/" + paused + "/pause", null).statusCode()).isEqualTo(200);
        var response = send("GET", ROOT, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var listedIds = new ArrayList<String>();
        for (var item : tree(response).path("items")) { listedIds.add(item.path("id").asText()); }
        assertThat(listedIds).containsExactlyInAnyOrder(never, fresh, stale, paused);
        for (var item : tree(response).path("items")) {
            var id = item.path("id").asText();
            if (id.equals(never)) {
                assertThat(item.path("lifecycle").asText()).isEqualTo("ACTIVE");
                assertThat(item.path("confirmedState").asText()).isEqualTo("UNKNOWN");
                assertThat(item.path("evidence").asText()).isEqualTo("NEVER_OBSERVED");
                assertThat(item.path("recentObservation").asBoolean()).isFalse();
            } else if (id.equals(fresh)) {
                assertThat(item.path("lifecycle").asText()).isEqualTo("ACTIVE");
                assertThat(item.path("confirmedState").asText()).isEqualTo("UP");
                assertThat(item.path("evidence").asText()).isEqualTo("FRESH");
                assertThat(item.path("recentObservation").asBoolean()).isTrue();
            } else if (id.equals(stale)) {
                assertThat(item.path("lifecycle").asText()).isEqualTo("ACTIVE");
                assertThat(item.path("confirmedState").asText()).isEqualTo("DOWN");
                assertThat(item.path("evidence").asText()).isEqualTo("STALE");
                assertThat(item.path("recentObservation").asBoolean()).isFalse();
                assertThat(item.path("openIncidentId").isNull()).isFalse();
            } else if (id.equals(paused)) {
                assertThat(item.path("lifecycle").asText()).isEqualTo("PAUSED");
                assertThat(item.path("confirmedState").asText()).isEqualTo("UNKNOWN");
                assertThat(item.path("evidence").asText()).isEqualTo("NEVER_OBSERVED");
                assertThat(item.path("recentObservation").asBoolean()).isFalse();
            }
        }
        assertThat(tree(response).path("items").size()).isEqualTo(4);
        assertThat(request("GET", ROOT, null, false, false).statusCode()).isEqualTo(401);
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,1.5", "page,invalid", "page,2147483648", "page,''",
            "size,0", "size,101", "size,2.5", "size,invalid", "size,''"})
    void paginationErrorsAreReportedPerField(String field, String value) throws Exception {
        assertInvalid(send("GET", ROOT + "?" + field + "=" + value, null), field);
        assertThat(count()).isZero();
    }

    @Test
    void bothInvalidPaginationFieldsAreReportedTogether() throws Exception {
        var response = send("GET", ROOT + "?page=-1&size=0", null);
        assertInvalid(response, "page");
        assertInvalid(response, "size");
        assertThat(tree(response).path("errors").size()).isEqualTo(2);
    }

    private JsonNode create() throws Exception {
        var response = send("POST", ROOT, valid());
        assertThat(response.statusCode()).isEqualTo(201);
        return tree(response);
    }

    private void assertHistory() {
        assertThat(jdbc.queryForObject("SELECT payload FROM monitor.api_history_fixture", String.class))
                .isEqualTo("original evidence");
    }

    private ObjectNode valid() {
        return json.createObjectNode().put("name", "Demo").put("url", "https://example.org/a")
                .put("intervalSeconds", 60).put("timeoutSeconds", 5).put("expectedStatus", 200)
                .put("failureThreshold", 3).put("recoveryThreshold", 2).put("alertRecipient", "ops@example.org");
    }

    private int count() { return jdbc.queryForObject("SELECT count(*) FROM monitor.monitors", Integer.class); }

    private JsonNode tree(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }

    private void assertInvalid(HttpResponse<String> response, String field) throws Exception {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(tree(response).path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(tree(response).path("errors").has(field)).isTrue();
        assertThat(tree(response).path("errors").path(field).asText()).isNotBlank();
    }

    private HttpResponse<String> send(String method, String path, JsonNode body) throws Exception {
        return request(method, path, body == null ? null : body.toString(), true, true);
    }

    private HttpResponse<String> request(String method, String path, String body, boolean basic, boolean csrf)
            throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(address + path));
        if (basic) {
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8)));
        }
        if (csrf && cookie != null) { builder.header("Cookie", cookie).header("X-CSRF-TOKEN", token); }
        if (body != null) { builder.header("Content-Type", "application/json"); }
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
