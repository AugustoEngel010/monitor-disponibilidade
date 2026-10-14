package br.com.monitor;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class IncidentFixtures {
    private IncidentFixtures() { }

    static void open(JdbcTemplate jdbc, UUID monitorId, UUID incidentId, long version) {
        if (incidentId == null) { return; }
        jdbc.update("INSERT INTO monitor.incidents (id, monitor_id, configuration_version, first_failure_at,"
                + " down_confirmed_at) VALUES (?, ?, ?, ?, ?)", incidentId, monitorId, version,
                Timestamp.from(Instant.parse("2026-10-13T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-10-13T00:02:00Z")));
    }
}
