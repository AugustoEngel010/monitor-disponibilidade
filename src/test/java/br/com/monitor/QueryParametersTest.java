package br.com.monitor;

import java.time.Instant;
import java.util.Map;
import br.com.monitor.administration.MonitorProblem;
import br.com.monitor.query.QueryParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryParametersTest {
    private final Instant now = Instant.parse("2026-10-18T12:00:00Z");

    @Test
    void calendarBoundsAreInclusiveAndOutOfRangeValuesHaveExactErrors() {
        var lower = parse(Map.of("from", "0001-01-02T00:00:00Z", "to", "0001-01-03T00:00:00Z"), "summary");
        assertThat(lower.from()).isEqualTo(Instant.parse("0001-01-02T00:00:00Z"));
        var values = new LinkedMultiValueMap<String, String>();
        values.add("from", "9999-12-29T00:00:00Z");
        values.add("to", "9999-12-30T00:00:00Z");
        var upper = QueryParameters.parse(values, "summary", Instant.parse("9999-12-31T00:00:00Z"));
        assertThat(upper.to()).isEqualTo(Instant.parse("9999-12-30T00:00:00Z"));
        assertThatThrownBy(() -> parse(Map.of("from", "0001-01-01T23:59:59Z"), "summary"))
                .isInstanceOfSatisfying(MonitorProblem.class, p -> assertThat(p.errors()).containsEntry("from",
                        "must be an ISO-8601 timestamp with offset and at most microsecond precision"));
        values.set("to", "9999-12-30T00:00:00.000001Z");
        assertThatThrownBy(() -> QueryParameters.parse(values, "summary", Instant.parse("9999-12-31T00:00:00Z")))
                .isInstanceOfSatisfying(MonitorProblem.class, p -> assertThat(p.errors()).containsEntry("to",
                        "must be an ISO-8601 timestamp with offset and at most microsecond precision"));
    }

    @Test
    void structuralErrorsHavePriorityOverInvalidValuesWithStableMessages() {
        var values = new LinkedMultiValueMap<String, String>();
        values.add("status", "OPEN");
        assertThatThrownBy(() -> QueryParameters.parse(values, "checks", now))
                .isInstanceOfSatisfying(MonitorProblem.class, p -> assertThat(p.errors())
                        .containsEntry("status", "unknown parameter"));
        values.clear();
        values.add("to", "bad");
        values.add("to", "bad");
        assertThatThrownBy(() -> QueryParameters.parse(values, "checks", now))
                .isInstanceOfSatisfying(MonitorProblem.class, p -> assertThat(p.errors())
                        .containsEntry("to", "must occur once"));
        values.clear();
        values.add("page", "-1");
        assertThatThrownBy(() -> QueryParameters.parse(values, "summary", now))
                .isInstanceOfSatisfying(MonitorProblem.class, p -> assertThat(p.errors())
                        .containsEntry("page", "unknown parameter"));
    }

    @Test
    void defaultsAndExplicitOffsetUseSameUtcWindow() {
        var defaults = parse(Map.of(), "checks");
        assertThat(defaults.from()).isEqualTo(now.minusSeconds(86400));
        assertThat(defaults.to()).isEqualTo(now);
        assertThat(defaults.page()).isZero();
        assertThat(defaults.size()).isEqualTo(20);
        var offset = parse(Map.of("from", "2026-10-18T08:00:00-03:00", "size", "100",
                "page", "2147483647", "outcome", "SUCCESS"), "checks");
        assertThat(offset.from()).isEqualTo(now.minusSeconds(3600));
        assertThat(offset.offset()).isEqualTo(214748364700L);
        assertThat(offset.outcome()).isEqualTo("SUCCESS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=2147483648", "page=1.5", "page=", "size=0", "size=101",
        "size=+1", "size=invalid", "from=2026-10-18T12:00:00", "from=2026-10-18T12:00:00Z",
        "to=2026-10-18T12:00:01Z", "from=2026-10-18T10:00:00.0000001Z", "outcome=BAD", "status=OPEN",
        "unknown=value"})
    void invalidParametersHaveFixedFieldErrors(String input) {
        String[] pair = input.split("=", -1);
        assertThatThrownBy(() -> parse(Map.of(pair[0], pair[1]), "checks"))
                .isInstanceOfSatisfying(MonitorProblem.class, problem -> {
                    assertThat(problem.status()).isEqualTo(400);
                    assertThat(problem.code()).isEqualTo("VALIDATION_ERROR");
                    assertThat(problem.errors()).containsKey(pair[0]);
                });
    }

    @Test
    void repeatedParametersAndWrongResourceFiltersAreRejected() {
        var values = new LinkedMultiValueMap<String, String>();
        values.add("page", "0");
        values.add("page", "1");
        assertThatThrownBy(() -> QueryParameters.parse(values, "checks", now)).isInstanceOf(MonitorProblem.class);
        assertThatThrownBy(() -> parse(Map.of("type", "DOWN"), "incidents"))
                .isInstanceOf(MonitorProblem.class);
        assertThat(parse(Map.of("status", "CLOSED"), "incidents").status()).isEqualTo("CLOSED");
        var notifications = parse(Map.of("status", "EXHAUSTED", "type", "RECOVERY"), "notifications");
        assertThat(notifications.status()).isEqualTo("EXHAUSTED");
        assertThat(notifications.type()).isEqualTo("RECOVERY");
        assertThatThrownBy(() -> parse(Map.of("status", "OPEN"), "notifications"))
                .isInstanceOf(MonitorProblem.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "1-1-1-1-1", "00000000-0000-0000-0000-000000000000x"})
    void identifiersMustBeCanonicalUuids(String id) {
        assertThatThrownBy(() -> QueryParameters.identifier(id)).isInstanceOf(MonitorProblem.class);
    }

    private QueryParameters parse(Map<String, String> input, String resource) {
        var values = new LinkedMultiValueMap<String, String>();
        input.forEach(values::add);
        return QueryParameters.parse(values, resource, now);
    }
}
