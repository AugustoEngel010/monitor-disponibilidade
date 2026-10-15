package br.com.monitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import br.com.monitor.notification.NotificationDelivery;
import br.com.monitor.notification.NotificationStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static br.com.monitor.NotificationFixture.BASE;
import static org.assertj.core.api.Assertions.assertThat;

class SmtpDurationIT {
    @Test
    void recoveryEmailPreservesExactFractionalSecondsFromIncidentEvidence() throws Exception {
        // MAIL-06: 12:03:01Z - 12:00:00.100Z = 180.9s, without integer truncation.
        try (var smtp = new GenericContainer<>(DockerImageName.parse(SmtpDeliveryIT.IMAGE))
                .withExposedPorts(1025, 8025); var client = HttpClient.newHttpClient()) {
            smtp.start();
            var delivery = new NotificationDelivery(UUID.randomUUID(), 1, UUID.randomUUID(), "RECOVERY",
                    "ops@example.org", "Fractional incident", "https://example.org", BASE.plusMillis(100),
                    BASE.plusSeconds(120), BASE.plusSeconds(181), BASE.plusSeconds(240));
            assertThat(SmtpDeliveryIT.transport(smtp.getHost(), smtp.getMappedPort(1025), 1000).send(delivery))
                    .isEqualTo(NotificationStore.Result.ACCEPTED);
            String origin = "http://" + smtp.getHost() + ":" + smtp.getMappedPort(8025);
            var messages = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/messages")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(messages.statusCode()).isEqualTo(200);
            var mapper = new ObjectMapper();
            var list = mapper.readTree(messages.body());
            assertThat(list.get("total").asInt()).isEqualTo(1);
            String id = list.get("messages").get(0).get("ID").asText();
            var message = client.send(HttpRequest.newBuilder(URI.create(origin + "/api/v1/message/" + id)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(message.statusCode()).isEqualTo(200);
            var body = mapper.readTree(message.body()).get("Text").asText();
            assertThat(body).contains("Primeira falha: 2026-10-15T12:00:00.100Z",
                    "Início da recuperação: 2026-10-15T12:03:01Z", "Duração estimada: 180.9 segundos");
        }
    }
}
