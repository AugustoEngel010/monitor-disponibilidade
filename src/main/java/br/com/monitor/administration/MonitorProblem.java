package br.com.monitor.administration;

import java.util.Map;

public class MonitorProblem extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, String> errors;

    public MonitorProblem(int status, String code, Map<String, String> errors) {
        super(code);
        this.status = status;
        this.code = code;
        this.errors = Map.copyOf(errors);
    }

    public int status() { return status; }
    public String code() { return code; }
    public Map<String, String> errors() { return errors; }

    public static MonitorProblem invalid(Map<String, String> errors) {
        return new MonitorProblem(400, "VALIDATION_ERROR", errors);
    }
}
