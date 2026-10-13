package br.com.monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationIT {
    @Test
    void readinessTracksRealDatabaseAvailability() throws Exception {
        // MON-02-AC01: UP with a real migrated database; DOWN when the database disappears.
        try (var db = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            db.start();
            try (var context = start(db, db.getPassword())) {
                int port = context.getWebServer().getPort();
                var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/actuator/health/readiness")).timeout(Duration.ofSeconds(10)).GET().build();
                var healthy = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(healthy.statusCode()).isEqualTo(200);
                assertThat(new ObjectMapper().readTree(healthy.body()).path("status").asText()).isEqualTo("UP");
                db.stop();
                var unhealthy = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(unhealthy.statusCode()).isEqualTo(503);
                assertThat(new ObjectMapper().readTree(unhealthy.body()).path("status").asText()).isEqualTo("DOWN");
            }
        }
    }

    @Test
    void startupRejectsInvalidDatabaseCredentials() {
        // MON-02-AC01/02: invalid database configuration cannot produce a healthy application.
        try (var db = new PostgreSQLContainer<>("postgres:17.6-alpine")) {
            db.start();
            assertThatThrownBy(() -> start(db, "invalid-password"))
                    .hasRootCauseInstanceOf(PSQLException.class)
                    .hasStackTraceContaining("password authentication failed");
        }
    }

    private ServletWebServerApplicationContext start(PostgreSQLContainer<?> db, String password) {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(MonitorApplication.class)
                .run("--server.port=0", "--spring.datasource.url=" + db.getJdbcUrl(),
                        "--spring.datasource.username=" + db.getUsername(), "--spring.datasource.password=" + password);
    }
}
