package br.com.monitor.operation;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {
    private final JdbcTemplate jdbc;

    public NotificationMetrics(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        for (String status : new String[] {"PENDING", "SENDING", "EXHAUSTED"}) {
            Gauge.builder("monitor.notifications", this, value -> value.count(status))
                    .tag("status", status).register(meters);
        }
    }

    private double count(String status) {
        try {
            return jdbc.queryForObject("SELECT count(*) FROM monitor.notification_intents WHERE status=?",
                    Long.class, status);
        } catch (RuntimeException error) { return Double.NaN; }
    }
}
