package br.com.monitor.query;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/monitors/{id}")
public class QueryController {
    private final HistoryQueries history;
    private final SummaryQueries summaries;
    private final Clock clock;

    public QueryController(HistoryQueries history, SummaryQueries summaries, Clock clock) {
        this.history = history;
        this.summaries = summaries;
        this.clock = clock;
    }

    @GetMapping("/checks")
    public HistoryQueries.Page<HistoryQueries.CheckView> checks(@PathVariable String id,
            @RequestParam MultiValueMap<String, String> parameters) {
        return history.checks(QueryParameters.identifier(id), query(parameters, "checks"));
    }

    @GetMapping("/incidents")
    public HistoryQueries.Page<HistoryQueries.IncidentView> incidents(@PathVariable String id,
            @RequestParam MultiValueMap<String, String> parameters) {
        return history.incidents(QueryParameters.identifier(id), query(parameters, "incidents"));
    }

    @GetMapping("/notifications")
    public HistoryQueries.Page<HistoryQueries.NotificationView> notifications(@PathVariable String id,
            @RequestParam MultiValueMap<String, String> parameters) {
        return history.notifications(QueryParameters.identifier(id), query(parameters, "notifications"));
    }

    @GetMapping("/summary")
    public SummaryQueries.Summary summary(@PathVariable String id,
            @RequestParam MultiValueMap<String, String> parameters) {
        return summaries.summary(QueryParameters.identifier(id), query(parameters, "summary"));
    }

    private QueryParameters query(MultiValueMap<String, String> parameters, String resource) {
        return QueryParameters.parse(parameters, resource, clock.instant().truncatedTo(ChronoUnit.MICROS));
    }
}
