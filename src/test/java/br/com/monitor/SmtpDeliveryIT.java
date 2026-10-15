package br.com.monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import br.com.monitor.notification.NotificationConfiguration;
import br.com.monitor.notification.NotificationDelivery;
import br.com.monitor.notification.SmtpDelivery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static br.com.monitor.NotificationFixture.BASE;
import static br.com.monitor.notification.NotificationStore.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmtpDeliveryIT {
    static final String IMAGE = "axllent/mailpit:v1.27.8";
    private final GenericContainer<?> smtp = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withExposedPorts(1025, 8025);

    @BeforeAll
    void start() { smtp.start(); }

    @AfterAll
    void stop() { smtp.stop(); }

    @Test
    void mailpitCapturesBothTransitionsWithFrozenPayloadDurationAndStableMessageId() throws Exception {
        var sender = transport(smtp.getHost(), smtp.getMappedPort(1025), 1000);
        var incident = UUID.randomUUID();
        var down = delivery(UUID.randomUUID(), incident, "DOWN");
        var recovery = delivery(UUID.randomUUID(), incident, "RECOVERY");
        for (var notification : java.util.List.of(down, down, recovery)) {
            assertThat(sender.send(notification)).isEqualTo(Result.ACCEPTED);
        }
        var messages = get("/api/v1/messages");
        assertThat(messages.get("total").asInt()).isEqualTo(3);
        int downCount = 0;
        for (var summary : messages.get("messages")) {
            var message = get("/api/v1/message/" + summary.get("ID").asText());
            boolean isRecovery = message.get("Subject").asText().contains("RECOVERY");
            var expected = isRecovery ? recovery : down;
            assertThat(message.get("MessageID").asText())
                    .isEqualTo(expected.messageId().replace("<", "").replace(">", ""));
            assertThat(message.get("To").get(0).get("Address").asText()).isEqualTo("ops@example.org");
            assertThat(message.get("From").get("Address").asText()).isEqualTo("monitor@example.org");
            assertThat(message.get("Subject").asText()).isEqualTo("[" + expected.type() + "] Original monitor");
            assertThat(message.get("Text").asText()).contains("Monitor: Original monitor", "URL: https://example.org",
                    "Transição: " + expected.type(), "Incidente: " + incident,
                    "Primeira falha: 2026-10-15T12:00:00Z", "Queda confirmada: 2026-10-15T12:02:00Z");
            if (isRecovery) {
                assertThat(message.get("Text").asText()).contains("Início da recuperação: 2026-10-15T12:03:00Z",
                        "Recuperação confirmada: 2026-10-15T12:04:00Z", "Duração estimada: 180 segundos",
                        "inclui pausas e lacunas");
            } else {
                downCount++;
                assertThat(message.get("Text").asText()).doesNotContain("Duração estimada:");
            }
        }
        assertThat(downCount).isEqualTo(2);
    }

    @Test
    void unavailableSmtpIsKnownFailureWithoutLeakingProviderError() throws Exception {
        int closedPort;
        try (var socket = new ServerSocket(0)) { closedPort = socket.getLocalPort(); }
        assertThat(transport("127.0.0.1", closedPort, 300).send(delivery(UUID.randomUUID(), UUID.randomUUID(), "DOWN")))
                .isEqualTo(Result.FAILED);
    }

    @Test
    void explicitSmtpRejectionIsFailed() throws Exception {
        try (var socket = new ServerSocket(0); var pool = Executors.newSingleThreadExecutor()) {
            var server = pool.submit(() -> {
                try (var client = socket.accept(); var output = new PrintWriter(client.getOutputStream(), true)) {
                    output.print("554 rejected secret@example.org password=secret\r\n"); output.flush();
                } catch (java.io.IOException error) { throw new IllegalStateException(error); }
            });
            assertThat(transport("127.0.0.1", socket.getLocalPort(), 300)
                    .send(delivery(UUID.randomUUID(), UUID.randomUUID(), "DOWN"))).isEqualTo(Result.FAILED);
            server.get(2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void timeoutAfterDataIsUncertainAndReturnsWithinFiniteReadTimeout() throws Exception {
        try (var socket = new ServerSocket(0); var pool = Executors.newSingleThreadExecutor()) {
            var server = pool.submit(() -> {
                try (var client = socket.accept();
                        var reader = new BufferedReader(new InputStreamReader(client.getInputStream(),
                                StandardCharsets.US_ASCII));
                        var output = new PrintWriter(client.getOutputStream(), true)) {
                    output.print("220 local SMTP\r\n"); output.flush();
                    boolean data = false;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (data && line.equals(".")) {
                            // Message stored, final 250 lost: caller's read timeout closes the socket.
                            return reader.readLine();
                        }
                        if (data) { continue; }
                        if (line.equals("DATA")) { data = true; output.print("354 continue\r\n"); }
                        else { output.print("250 OK\r\n"); }
                        output.flush();
                    }
                } catch (java.io.IOException error) { throw new IllegalStateException(error); }
                return null;
            });
            long started = System.nanoTime();
            assertThat(transport("127.0.0.1", socket.getLocalPort(), 300)
                    .send(delivery(UUID.randomUUID(), UUID.randomUUID(), "DOWN"))).isEqualTo(Result.UNCERTAIN);
            assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isBetween(250L, 2500L);
            server.get(3, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void sendingInsideDomainTransactionIsRejectedBeforeNetwork() {
        // No DB access required to establish transaction-bound guard.
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> transport(smtp.getHost(), smtp.getMappedPort(1025), 300)
                    .send(delivery(UUID.randomUUID(), UUID.randomUUID(), "DOWN")))
                    .isInstanceOf(IllegalStateException.class).hasMessage("SMTP cannot run inside a transaction");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    static SmtpDelivery transport(String host, int port, int timeout) {
        var environment = new MockEnvironment().withProperty("monitor.smtp.host", host)
                .withProperty("monitor.smtp.port", Integer.toString(port))
                .withProperty("monitor.smtp.connect-timeout-ms", Integer.toString(timeout))
                .withProperty("monitor.smtp.read-timeout-ms", Integer.toString(timeout))
                .withProperty("monitor.smtp.write-timeout-ms", Integer.toString(timeout));
        var configuration = new NotificationConfiguration();
        return configuration.smtpDelivery(configuration.notificationMailSender(environment), environment);
    }

    private NotificationDelivery delivery(UUID id, UUID incident, String type) {
        return new NotificationDelivery(id, 1, incident, type, "ops@example.org", "Original monitor",
                "https://example.org", BASE, BASE.plusSeconds(120), type.equals("RECOVERY") ? BASE.plusSeconds(180)
                        : null, type.equals("RECOVERY") ? BASE.plusSeconds(240) : null);
    }

    private JsonNode get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://" + smtp.getHost() + ":"
                    + smtp.getMappedPort(8025) + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            return new ObjectMapper().readTree(response.body());
        }
    }
}
