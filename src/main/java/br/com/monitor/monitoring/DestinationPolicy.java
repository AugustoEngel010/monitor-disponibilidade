package br.com.monitor.monitoring;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import br.com.monitor.administration.MonitorConfiguration;

public final class DestinationPolicy {
    private final URI demoOrigin;

    public DestinationPolicy(String origin) {
        try {
            demoOrigin = origin == null || origin.isBlank() ? null : URI.create(origin.trim());
            if (demoOrigin != null) {
                validateSyntax(demoOrigin);
                if (demoOrigin.getRawPath() != null && !demoOrigin.getRawPath().isEmpty()
                        && !demoOrigin.getRawPath().equals("/")) { throw new Blocked(); }
            }
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid demonstration origin");
        }
    }

    public void validate(URI uri) {
        validateSyntax(uri);
        String host = host(uri);
        if (host.contains(":")) {
            try { validateAddresses(uri, new InetAddress[]{InetAddress.getByName(host)}); }
            catch (UnknownHostException error) { throw new Blocked(); }
        } else if (host.matches("[0-9.]+")) {
            // Reject alternate numeric forms before a client can interpret them differently.
            if (!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) { throw new Blocked(); }
            try { validateAddresses(uri, new InetAddress[]{InetAddress.getByName(host)}); }
            catch (UnknownHostException error) { throw new Blocked(); }
        }
    }

    public InetAddress[] validateAddresses(URI uri, InetAddress[] addresses) throws UnknownHostException {
        if (addresses == null || addresses.length == 0) { throw new UnknownHostException(); }
        for (var address : addresses) {
            if (address == null || (!isDemo(uri) && !isPublic(address))) { throw new Blocked(); }
        }
        return addresses.clone();
    }

    public static String host(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
    }

    private static void validateSyntax(URI uri) {
        try {
            MonitorConfiguration.validated("check", uri.toString(), 60, 5, 200, 3, 2, "ops@example.org");
        } catch (RuntimeException error) { throw new Blocked(); }
    }

    private boolean isDemo(URI uri) {
        return demoOrigin != null && uri.getScheme().equalsIgnoreCase(demoOrigin.getScheme())
                && host(uri).equals(host(demoOrigin)) && port(uri) == port(demoOrigin);
    }

    private static int port(URI uri) {
        return uri.getPort() == -1 ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
    }

    private static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int a = bytes[0] & 255;
            int b = bytes[1] & 255;
            int c = bytes[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || (a == 100 && b >= 64 && b <= 127) || (a == 169 && b == 254)
                    || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168)
                    || (a == 192 && b == 0 && (c == 0 || c == 2)) || (a == 192 && b == 88 && c == 99)
                    || (a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                    || (a == 203 && b == 0 && c == 113));
        }
        int a = bytes[0] & 255;
        int b = bytes[1] & 255;
        int c = bytes[2] & 255;
        int d = bytes[3] & 255;
        return (a & 224) == 32 && !(a == 32 && b == 1 && c <= 1)
                && !(a == 32 && b == 1 && c == 13 && d == 184)
                && !(a == 32 && b == 2) && !(a == 63 && b == 255 && (c & 240) == 0);
    }

    public static final class Blocked extends RuntimeException {
        public Blocked() { super("Destination blocked by policy"); }
    }
}
