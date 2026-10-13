package br.com.monitor;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class AdministrativeSecurityIT {
    private final String username = UUID.randomUUID().toString();
    private final String password = UUID.randomUUID().toString();
    private final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:17.6-alpine");
    private final HttpClient client = HttpClient.newHttpClient();
    private ServletWebServerApplicationContext context;
    private String address;

    @BeforeAll
    void start() {
        db.start();
        context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(
                MonitorApplication.class, ProbeConfiguration.class)
                .run("--server.port=0", "--spring.datasource.url=" + db.getJdbcUrl(),
                        "--spring.datasource.username=" + db.getUsername(),
                        "--spring.datasource.password=" + db.getPassword(),
                        "--ADMIN_USERNAME=" + username, "--ADMIN_PASSWORD=" + password);
        address = "http://localhost:" + context.getWebServer().getPort();
    }

    @AfterAll
    void stop(CapturedOutput output) {
        if (context != null) {
            context.close();
        }
        db.stop();
        // MON-03-AC05: startup, successful requests, bad authentication and shutdown logs.
        assertThat(output.getAll()).doesNotContain(username, password, basic(username, password),
                "Using generated security password");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/admin/probe", "/api/admin/probe", "/actuator/info",
            "/actuator/env", "/assets/probe", "/error", "/unlisted", "/actuator/health/other"})
    void everyAdministrativePathRequiresBasic(String path) throws Exception {
        // MON-03-AC01/03: global policy also covers the future panel and unlisted routes.
        var response = send(path, "GET", null);
        assertUnauthorized(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void missingAndInvalidCredentialsAlwaysReturn401(String method) throws Exception {
        // MON-03-AC01: authentication takes precedence even when a write has no CSRF token.
        for (String header : new String[] {null, basic(username, "wrong-password"),
                basic("wrong-operator", password), "Basic %%%", "Bearer invalid"}) {
            assertUnauthorized(send("/api/admin/probe", method, header));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/admin/probe", "/api/admin/probe", "/assets/probe", "/actuator/info"})
    void validBasicAllowsAdministrativeReads(String path) throws Exception {
        // MON-03-AC02/03: real resources and test-only panel/API endpoints.
        var response = send(path, "GET", basic(username, password));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain(username, password, basic(username, password));
    }

    @Test
    void validBasicDoesNotCreateMissingResources() throws Exception {
        assertThat(send("/unlisted", "GET", basic(username, password)).statusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"})
    void healthIsPublicMinimalAndDoesNotCreateSession(String path) throws Exception {
        // MON-03-AC03: probes need no secret and disclose only status.
        var response = send(path, "GET", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
        assertThat(send(path, "HEAD", null).statusCode()).isEqualTo(200);
        assertUnauthorized(send(path, "POST", null));
    }

    @Test
    void csrfEndpointRequiresBasicAndReturnsSessionTokenWithoutCors() throws Exception {
        // MON-03-AC01/05/06: another origin cannot read the token; the cookie alone cannot authenticate.
        assertUnauthorized(send("/api/admin/csrf", "GET", null));
        assertUnauthorized(send("/api/admin/csrf", "GET", basic(username, "wrong-password")));
        var request = HttpRequest.newBuilder(URI.create(address + "/api/admin/csrf"))
                .header("Authorization", basic(username, password)).header("Origin", "https://forged.example")
                .GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var token = new ObjectMapper().readTree(response.body());
        assertThat(token.path("token").asText()).isNotBlank();
        assertThat(token.path("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        assertThat(token.path("parameterName").asText()).isEqualTo("_csrf");
        assertThat(response.headers().firstValue("set-cookie").orElseThrow())
                .contains("JSESSIONID=", "HttpOnly", "SameSite=Lax");
        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(response.body()).doesNotContain(username, password, basic(username, password));
        assertUnauthorized(client.send(HttpRequest.newBuilder(URI.create(address + "/actuator/info"))
                .header("Cookie", cookie(response)).GET().build(), HttpResponse.BodyHandlers.ofString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void csrfProtectsEveryAdministrativeMutationAndRejectsForgedForms(String method) throws Exception {
        // MON-03-AC06: failures have no side effect; both header and form contracts work.
        var session = csrfSession();
        var otherSession = csrfSession();
        var probe = context.getBean(ProbeController.class);
        String authorization = basic(username, password);
        for (String path : new String[] {"/api/admin/probe", "/admin/probe"}) {
            int before = probe.mutations.get();
            assertForbidden(mutate(path, method, authorization, session.cookie(), null, false, false));
            assertForbidden(mutate(path, method, authorization, session.cookie(), "invalid", false, false));
            assertForbidden(mutate(path, method, authorization, otherSession.cookie(),
                    session.token(), false, false));
            assertForbidden(mutate(path, method, authorization, null, session.token(), false, false));
            assertForbidden(mutate(path, method, authorization, session.cookie(), null, true, true));
            assertUnauthorized(mutate(path, method, null, session.cookie(), session.token(), false, false));
            assertUnauthorized(mutate(path, method, basic(username, "wrong-password"),
                    session.cookie(), session.token(), false, false));
            assertThat(probe.mutations.get()).isEqualTo(before);

            var validHeader = mutate(path, method, authorization, session.cookie(), session.token(), false, false);
            assertThat(validHeader.statusCode()).isEqualTo(200);
            assertThat(validHeader.body()).isEqualTo(Integer.toString(before + 1));
            assertThat(probe.mutations.get()).isEqualTo(before + 1);

            var validForm = mutate(path, method, authorization, session.cookie(), session.token(), true, false);
            assertThat(validForm.statusCode()).isEqualTo(200);
            assertThat(validForm.body()).isEqualTo(Integer.toString(before + 2));
            assertThat(probe.mutations.get()).isEqualTo(before + 2);
        }
    }

    private void assertForbidden(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).isEmpty();
    }

    private CsrfSession csrfSession() throws Exception {
        var response = send("/api/admin/csrf", "GET", basic(username, password));
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = new ObjectMapper().readTree(response.body());
        return new CsrfSession(cookie(response), body.path("token").asText());
    }

    private String cookie(HttpResponse<String> response) {
        return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }

    private HttpResponse<String> mutate(String path, String method, String authorization, String cookie,
            String token, boolean form, boolean crossOrigin) throws Exception {
        String body = form && token != null ? "_csrf=" + URLEncoder.encode(token, StandardCharsets.UTF_8) : "";
        var request = HttpRequest.newBuilder(URI.create(address + path))
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (!form && token != null) {
            request.header("X-CSRF-TOKEN", token);
        }
        if (form) {
            request.header("Content-Type", "application/x-www-form-urlencoded");
        }
        if (crossOrigin) {
            request.header("Origin", "https://forged.example");
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private record CsrfSession(String cookie, String token) { }

    private void assertUnauthorized(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .contains("Basic realm=\"monitor-admin\"");
        assertThat(response.body()).isEmpty();
    }

    private HttpResponse<String> send(String path, String method, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(address + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String basic(String user, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + secret)
                .getBytes(StandardCharsets.UTF_8));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {
        private final AtomicInteger mutations = new AtomicInteger();

        @RequestMapping(path = {"/", "/admin/probe", "/api/admin/probe", "/assets/probe"},
                method = RequestMethod.GET)
        String read() {
            return "administrative-resource";
        }

        @RequestMapping(path = {"/admin/probe", "/api/admin/probe"},
                method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
        int mutate() {
            return mutations.incrementAndGet();
        }
    }
}
