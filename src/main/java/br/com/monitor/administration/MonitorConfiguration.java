package br.com.monitor.administration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public record MonitorConfiguration(String name, String url, int intervalSeconds, int timeoutSeconds,
        int expectedStatus, int failureThreshold, int recoveryThreshold, String alertRecipient) {
    private static final Pattern LOCAL = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*");
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?");

    public static MonitorConfiguration validated(String name, String url, Integer interval, Integer timeout,
            Integer status, Integer failures, Integer recoveries, String recipient) {
        name = trim(name);
        url = trim(url);
        recipient = trim(recipient);
        int i = interval == null ? 60 : interval;
        int t = timeout == null ? 5 : timeout;
        int s = status == null ? 200 : status;
        int f = failures == null ? 3 : failures;
        int r = recoveries == null ? 2 : recoveries;
        var errors = new LinkedHashMap<String, String>();
        if (name == null || name.isBlank() || name.length() > 100) {
            errors.put("name", "must contain 1 to 100 characters");
        }
        if (!validUrl(url)) { errors.put("url", "must be HTTP/HTTPS without credentials, query or fragment"); }
        range(errors, "intervalSeconds", i, 10, 3600);
        range(errors, "timeoutSeconds", t, 1, 30);
        if (t >= i) { errors.put("timeoutSeconds", "must be less than intervalSeconds"); }
        range(errors, "expectedStatus", s, 200, 599);
        range(errors, "failureThreshold", f, 1, 10);
        range(errors, "recoveryThreshold", r, 1, 10);
        if (!validEmail(recipient)) { errors.put("alertRecipient", "must be a single email up to 254 characters"); }
        if (!errors.isEmpty()) { throw MonitorProblem.invalid(errors); }
        return new MonitorConfiguration(name, url, i, t, s, f, r, recipient);
    }

    public boolean samePolicy(MonitorConfiguration other) {
        return url.equals(other.url) && intervalSeconds == other.intervalSeconds
                && timeoutSeconds == other.timeoutSeconds && expectedStatus == other.expectedStatus
                && failureThreshold == other.failureThreshold && recoveryThreshold == other.recoveryThreshold;
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }

    private static void range(Map<String, String> errors, String field, int value, int min, int max) {
        if (value < min || value > max) { errors.put(field, "must be between " + min + " and " + max); }
    }

    private static boolean validUrl(String value) {
        if (value == null || value.length() > 2048) { return false; }
        try {
            var uri = new URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) { return false; }
            var authority = uri.getRawAuthority();
            if (authority.endsWith(":")) { return false; }
            return uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535);
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static boolean validEmail(String value) {
        if (value == null || value.length() > 254) { return false; }
        var parts = value.split("@", -1);
        if (parts.length != 2 || parts[0].length() > 64 || !LOCAL.matcher(parts[0]).matches()) { return false; }
        var labels = parts[1].split("\\.", -1);
        if (labels.length < 2) { return false; }
        for (var label : labels) { if (!LABEL.matcher(label).matches()) { return false; } }
        return true;
    }
}
