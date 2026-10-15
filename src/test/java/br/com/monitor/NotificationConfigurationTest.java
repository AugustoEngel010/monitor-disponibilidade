package br.com.monitor;

import br.com.monitor.notification.NotificationConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationConfigurationTest {
    @Test
    void defaultTimeoutsAreFiniteAndEnvironmentConfiguresAuthenticatedTls() {
        var environment = new MockEnvironment().withProperty("monitor.smtp.host", "smtp.example.org")
                .withProperty("monitor.smtp.port", "587").withProperty("monitor.smtp.username", "test-user")
                .withProperty("monitor.smtp.password", "test-password").withProperty("monitor.smtp.auth", "true")
                .withProperty("monitor.smtp.starttls", "true");
        var sender = (JavaMailSenderImpl) new NotificationConfiguration().notificationMailSender(environment);
        assertThat(sender.getHost()).isEqualTo("smtp.example.org");
        assertThat(sender.getPort()).isEqualTo(587);
        assertThat(sender.getUsername()).isEqualTo("test-user");
        assertThat(sender.getPassword()).isEqualTo("test-password");
        assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "3000").containsEntry("mail.smtp.writetimeout", "5000")
                .containsEntry("mail.smtp.auth", "true").containsEntry("mail.smtp.starttls.enable", "true")
                .containsEntry("mail.smtp.starttls.required", "true").containsEntry("mail.smtp.ssl.enable", "false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"connect-timeout-ms", "read-timeout-ms", "write-timeout-ms", "port"})
    void invalidBoundsFailWithSanitizedConfigurationError(String field) {
        for (var value : java.util.List.of("0", "-1", "invalid-secret", "999999999")) {
            var environment = new MockEnvironment().withProperty("monitor.smtp." + field, value);
            assertThatThrownBy(() -> new NotificationConfiguration().notificationMailSender(environment))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid SMTP configuration");
        }
    }

    @Test
    void conflictingTlsModesAndInvalidSenderFailClosed() {
        var configuration = new NotificationConfiguration();
        assertThatThrownBy(() -> configuration.notificationMailSender(new MockEnvironment()
                .withProperty("monitor.smtp.ssl", "true").withProperty("monitor.smtp.starttls", "true")))
                .hasMessage("Invalid SMTP configuration");
        assertThatThrownBy(() -> configuration.smtpDelivery(configuration.notificationMailSender(new MockEnvironment()),
                new MockEnvironment().withProperty("monitor.smtp.from", "bad\r\nBcc: secret@example.org")))
                .hasMessage("Invalid SMTP configuration");
    }
}
