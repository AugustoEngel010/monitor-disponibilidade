package br.com.monitor;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class AdministrativeConfigurationIT {
    @Container
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6-alpine");

    static Stream<Arguments> invalidConfiguration() {
        String valid = UUID.randomUUID().toString();
        return Stream.of(
                Arguments.of(null, valid), Arguments.of(valid, null),
                Arguments.of("", valid), Arguments.of(valid, ""),
                Arguments.of("   ", valid), Arguments.of(valid, "   "),
                Arguments.of("<choose-admin-username>", valid),
                Arguments.of(valid, "<choose-admin-password>"),
                Arguments.of("a:b", valid), Arguments.of("a\nb", valid),
                Arguments.of("x".repeat(129), valid), Arguments.of(valid, "x".repeat(73)),
                Arguments.of(valid, "á".repeat(37)));
    }

    @ParameterizedTest(name = "reject invalid configuration #{index}")
    @MethodSource("invalidConfiguration")
    void invalidCredentialsPreventStartupWithoutLeakingValues(String username, String password,
            CapturedOutput output) {
        // MON-03-AC04/05: no valid administrative configuration means no running server.
        var application = new SpringApplicationBuilder(MonitorApplication.class);
        var arguments = new java.util.ArrayList<String>();
        arguments.add("--server.port=0");
        arguments.add("--spring.datasource.url=" + DB.getJdbcUrl());
        arguments.add("--spring.datasource.username=" + DB.getUsername());
        arguments.add("--spring.datasource.password=" + DB.getPassword());
        if (username != null) {
            arguments.add("--ADMIN_USERNAME=" + username);
        }
        if (password != null) {
            arguments.add("--ADMIN_PASSWORD=" + password);
        }
        assertThatThrownBy(() -> {
            try (var context = application.run(arguments.toArray(String[]::new))) {
                // Close even an incorrectly started application before the assertion fails.
            }
        }).hasRootCauseMessage("Administrative credentials are missing or invalid");
        if (username != null && !username.isBlank()) {
            assertThat(output.getAll()).doesNotContain(username);
        }
        if (password != null && !password.isBlank()) {
            assertThat(output.getAll()).doesNotContain(password);
        }
        assertThat(output.getAll()).doesNotContain("Using generated security password");
    }
}
