package br.com.monitor;

import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.administration.MonitorProblem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitorConfigurationTest {
    @Test
    void defaultsAndTrimmingFollowMon01() {
        var config = MonitorConfiguration.validated(" Demo ", " https://example.org/a ",
                null, null, null, null, null, " ops+monitor@example.org ");
        assertThat(config).isEqualTo(new MonitorConfiguration("Demo", "https://example.org/a",
                60, 5, 200, 3, 2, "ops+monitor@example.org"));
    }

    @Test
    void inclusiveLimitsAreAccepted() {
        assertThat(MonitorConfiguration.validated("n", "http://example.org:1", 10, 9, 200, 1, 1,
                "a@example.org").timeoutSeconds()).isEqualTo(9);
        assertThat(MonitorConfiguration.validated("n".repeat(100), "https://example.org:65535",
                3600, 30, 599, 10, 10, "a@example.org").name()).hasSize(100);
        var url = "http://example.org/" + "x".repeat(2029);
        assertThat(MonitorConfiguration.validated("n", url, 10, 1, 200, 1, 1,
                "a@example.org").url()).hasSize(2048);
    }

    @Test
    void allInvalidFieldsAreReportedTogether() {
        try {
            MonitorConfiguration.validated(" ", "ftp://example.org", 9, 31, 199, 0, 11, "invalid");
            throw new AssertionError("invalid configuration accepted");
        } catch (MonitorProblem problem) {
            assertThat(problem.status()).isEqualTo(400);
            assertThat(problem.errors()).containsOnlyKeys("name", "url", "intervalSeconds", "timeoutSeconds",
                    "expectedStatus", "failureThreshold", "recoveryThreshold", "alertRecipient");
        }
    }

    @Test
    void oppositeNumericBoundariesAndTimeoutEqualityAreRejected() {
        rejects("intervalSeconds", () -> config(3601, 5, 200, 3, 2));
        rejects("timeoutSeconds", () -> config(60, 0, 200, 3, 2));
        rejects("timeoutSeconds", () -> config(10, 10, 200, 3, 2));
        rejects("expectedStatus", () -> config(60, 5, 600, 3, 2));
        rejects("failureThreshold", () -> config(60, 5, 200, 11, 2));
        rejects("recoveryThreshold", () -> config(60, 5, 200, 3, 0));
        rejects("name", () -> MonitorConfiguration.validated("n".repeat(101), "https://example.org",
                null, null, null, null, null, "a@example.org"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://user:secret@example.org", "https://@example.org", "file:///tmp/a",
            "ftp://example.org", "/relative", "http:///missing", "https://example.org?",
            "https://example.org?q=token", "https://example.org#", "https://example.org#fragment",
            "http://example.org:0", "http://example.org:65536", "http://example.org:",
            "http://example.org:abc", "https://exa mple.org", "https://example.org\\path"})
    void invalidUrlsNeverEchoCredentials(String url) {
        rejects("url", () -> MonitorConfiguration.validated("n", url, null, null, null, null, null,
                "a@example.org"));
    }

    @Test
    void missingAndOversizedFieldsAreRejected() {
        for (String field : new String[] {"name", "url", "alertRecipient"}) {
            rejects(field, () -> MonitorConfiguration.validated(null, null, null, null, null, null, null, null));
        }
        rejects("url", () -> MonitorConfiguration.validated("n", "http://example.org/" + "x".repeat(2030),
                null, null, null, null, null, "a@example.org"));
        rejects("alertRecipient", () -> MonitorConfiguration.validated("n", "http://example.org",
                null, null, null, null, null, "a".repeat(65) + "@example.org"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080/a", "HTTPS://example.org", "http://127.0.0.1",
            "http://[::1]:8080/a", "https://example.org/a%20b"})
    void administrativeSyntaxDoesNotResolveOrBlockPrivateDestinations(String url) {
        assertThat(MonitorConfiguration.validated("n", url, null, null, null, null, null,
                "a@example.org").url()).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"A <a@example.org>", "a@example.org,b@example.org", "a@example.org;b@example.org",
            "a..b@example.org", ".a@example.org", "a.@example.org", "a@localhost", "a@-example.org",
            "a@example-.org", "a b@example.org", "\"a\"@example.org", "á@example.org"})
    void onlySingleAsciiDotAtomEmailIsAccepted(String email) {
        rejects("alertRecipient", () -> MonitorConfiguration.validated("n", "https://example.org",
                null, null, null, null, null, email));
    }

    @Test
    void emailLengthBoundaryIsExplicit() {
        var email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertThat(email).hasSize(254);
        assertThat(MonitorConfiguration.validated("n", "https://example.org", null, null, null, null, null,
                email).alertRecipient()).isEqualTo(email);
        rejects("alertRecipient", () -> MonitorConfiguration.validated("n", "https://example.org",
                null, null, null, null, null, email + "e"));
    }

    private MonitorConfiguration config(int interval, int timeout, int status, int failures, int recoveries) {
        return MonitorConfiguration.validated("n", "https://example.org", interval, timeout, status,
                failures, recoveries, "a@example.org");
    }

    private void rejects(String field, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(MonitorProblem.class, problem -> {
            assertThat(problem.status()).isEqualTo(400);
            assertThat(problem.errors()).containsKey(field);
            assertThat(problem.getMessage()).doesNotContain("secret", "token@example");
        });
    }
}
