package br.com.monitor;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import br.com.monitor.administration.Monitor;
import br.com.monitor.administration.MonitorConfiguration;
import br.com.monitor.monitoring.CheckResult;
import br.com.monitor.monitoring.DestinationPolicy;
import br.com.monitor.monitoring.HttpCheckExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static br.com.monitor.monitoring.CheckResult.Outcome;
import static org.assertj.core.api.Assertions.assertThat;

class HttpCheckExecutorIT {
    @ParameterizedTest
    @CsvSource({"200,200,SUCCESS", "200,503,HTTP_FAILURE", "200,301,HTTP_FAILURE", "301,301,SUCCESS"})
    void classifiesHeadersAndClosesWithoutReadingEndlessBody(int expected, int received, Outcome outcome)
            throws Exception {
        try (var peer = new Peer(received, true)) {
            var monitor = monitor(peer.url(), expected);
            var id = UUID.randomUUID();
            var result = executor(peer.url()).execute(monitor, id);
            assertThat(result.outcome()).isEqualTo(outcome);
            assertThat(result.httpStatus()).isEqualTo(received);
            assertThat(result.monitorId()).isEqualTo(monitor.id());
            assertThat(result.executionId()).isEqualTo(id);
            assertThat(result.configurationVersion()).isEqualTo(1);
            assertThat(result.executionGeneration()).isEqualTo(1);
            assertThat(result.finishedAt()).isAfterOrEqualTo(result.startedAt());
            assertThat(result.durationNanos()).isBetween(1L, TimeUnit.MILLISECONDS.toNanos(900));
            assertThat(peer.closed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(peer.method.get()).isEqualTo("GET /check HTTP/1.1");
            assertThat(peer.connections.get()).isEqualTo(1);
            assertThat(result.errorMessage()).isEqualTo(outcome.message());
        }
    }

    @Test
    void slowHeadersTimeOutAndCloseConnection() throws Exception {
        try (var peer = new Peer(200, false)) {
            var result = executor(peer.url()).execute(monitor(peer.url(), 200), UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.TIMEOUT);
            assertThat(result.httpStatus()).isNull();
            assertThat(result.durationNanos()).isBetween(TimeUnit.MILLISECONDS.toNanos(900),
                    TimeUnit.MILLISECONDS.toNanos(1500));
            assertThat(peer.closed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void durationIncludesDnsAndUtcMetadataComesFromClock() throws Exception {
        try (var peer = new Peer(200, true)) {
            var instant = Instant.parse("2026-10-13T12:00:00Z");
            var executor = new HttpCheckExecutor(new DestinationPolicy(origin(peer.url())), host -> {
                try { Thread.sleep(200); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                return new InetAddress[]{InetAddress.getByName("127.0.0.1")};
            }, Clock.fixed(instant, java.time.ZoneOffset.UTC));
            var result = executor.execute(monitor(peer.url(), 200), UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(result.startedAt()).isEqualTo(instant);
            assertThat(result.finishedAt()).isEqualTo(instant);
            assertThat(result.durationNanos()).isBetween(TimeUnit.MILLISECONDS.toNanos(190),
                    TimeUnit.MILLISECONDS.toNanos(900));
            assertThat(peer.closed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void alreadyInterruptedCallerDoesNotResolveOrConnect() {
        var calls = new AtomicInteger();
        var executor = new HttpCheckExecutor(new DestinationPolicy(""), host -> {
            calls.incrementAndGet();
            return new InetAddress[]{InetAddress.getLoopbackAddress()};
        }, Clock.systemUTC());
        try {
            Thread.currentThread().interrupt();
            var result = executor.execute(monitor("http://public.example", 200), UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.CANCELLED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(calls.get()).isZero();
        } finally { Thread.interrupted(); }
    }

    @Test
    void interruptionCancelsAndReleasesConnection() throws Exception {
        try (var peer = new Peer(200, false)) {
            var result = new AtomicReference<CheckResult>();
            var interrupted = new AtomicReference<Boolean>();
            Thread caller = Thread.ofVirtual().start(() -> {
                result.set(executor(peer.url()).execute(monitor(peer.url(), 200), UUID.randomUUID()));
                interrupted.set(Thread.currentThread().isInterrupted());
            });
            assertThat(peer.accepted.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(2000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(result.get().outcome()).isEqualTo(Outcome.CANCELLED);
            assertThat(result.get().httpStatus()).isNull();
            assertThat(interrupted.get()).isTrue();
            assertThat(peer.closed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void interruptedConnectionIsFailureWithoutAutomaticRetry() throws Exception {
        try (var server = new ServerSocket(0)) {
            var accepted = new AtomicInteger();
            Thread peer = Thread.ofVirtual().start(() -> {
                try (var socket = server.accept()) { accepted.incrementAndGet(); }
                catch (IOException ignored) { }
            });
            String url = "http://127.0.0.1:" + server.getLocalPort();
            var result = executor(url).execute(monitor(url, 200), UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.CONNECTION_FAILURE);
            assertThat(result.httpStatus()).isNull();
            assertThat(accepted.get()).isEqualTo(1);
            peer.join(2000);
        }
    }

    @Test
    void refusedConnectionIsClassified() throws Exception {
        int port;
        try (var server = new ServerSocket(0)) { port = server.getLocalPort(); }
        String url = "http://127.0.0.1:" + port;
        var result = executor(url).execute(monitor(url, 200), UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(Outcome.CONNECTION_FAILURE);
        assertThat(result.httpStatus()).isNull();
    }

    @Test
    void dnsFailureIsSanitized() {
        var executor = new HttpCheckExecutor(new DestinationPolicy(""), host -> {
            throw new UnknownHostException("secret-password https://private.example");
        }, Clock.systemUTC());
        var result = executor.execute(monitor("https://public.example", 200), UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(Outcome.DNS_FAILURE);
        assertThat(result.errorMessage()).isEqualTo("DNS resolution failed");
        assertThat(result.httpStatus()).isNull();
        assertThat(result.toString()).doesNotContain("secret-password", "private.example");
    }

    @Test
    void dnsIsInsideTotalDeadlineAndCannotConnectAfterTimeout() throws Exception {
        try (var peer = new Peer(200, true)) {
            var release = new CountDownLatch(1);
            var completed = new CountDownLatch(1);
            var executor = new HttpCheckExecutor(new DestinationPolicy(origin(peer.url())), host -> {
                // Model a native resolver that temporarily ignores interruption.
                boolean done = false;
                while (!done) {
                    try { done = release.await(2, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { }
                }
                completed.countDown();
                return new InetAddress[]{InetAddress.getLoopbackAddress()};
            }, Clock.systemUTC());
            try {
                var result = executor.execute(monitor(peer.url(), 200), UUID.randomUUID());
                assertThat(result.outcome()).isEqualTo(Outcome.TIMEOUT);
                assertThat(result.durationNanos()).isLessThan(TimeUnit.MILLISECONDS.toNanos(1500));
            } finally { release.countDown(); }
            assertThat(completed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(peer.accepted.await(200, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(peer.connections.get()).isZero();
        }
    }

    @Test
    void cancellationDuringDnsCannotConnectLater() throws Exception {
        try (var peer = new Peer(200, true)) {
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var executor = new HttpCheckExecutor(new DestinationPolicy(origin(peer.url())), host -> {
                entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { }
                return new InetAddress[]{InetAddress.getLoopbackAddress()};
            }, Clock.systemUTC());
            var result = new AtomicReference<CheckResult>();
            Thread caller = Thread.ofVirtual().start(() ->
                    result.set(executor.execute(monitor(peer.url(), 200), UUID.randomUUID())));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            release.countDown();
            caller.join(2000);
            assertThat(caller.isAlive()).isFalse();
            assertThat(result.get().outcome()).isEqualTo(Outcome.CANCELLED);
            assertThat(peer.accepted.await(200, TimeUnit.MILLISECONDS)).isFalse();
        }
    }

    @Test
    void blocksLiteralBeforeDnsAndMixedDnsBeforeConnection() throws Exception {
        var calls = new AtomicInteger();
        var executor = new HttpCheckExecutor(new DestinationPolicy(""), host -> {
            calls.incrementAndGet();
            return new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getLoopbackAddress()};
        }, Clock.systemUTC());
        var literal = executor.execute(monitor("http://127.0.0.1", 200), UUID.randomUUID());
        assertThat(literal.outcome()).isEqualTo(Outcome.DESTINATION_BLOCKED);
        assertThat(calls.get()).isZero();
        var mixed = executor.execute(monitor("http://public.example", 200), UUID.randomUUID());
        assertThat(mixed.outcome()).isEqualTo(Outcome.DESTINATION_BLOCKED);
        assertThat(mixed.httpStatus()).isNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void resolutionSnapshotIsUsedForConnectionWithoutSecondLookup() throws Exception {
        try (var peer = new Peer(200, true)) {
            String url = peer.url().replace("127.0.0.1", "demo.example");
            var calls = new AtomicInteger();
            var executor = new HttpCheckExecutor(new DestinationPolicy(origin(url)), host -> {
                int call = calls.incrementAndGet();
                return new InetAddress[]{InetAddress.getByName(call == 1 ? "127.0.0.1" : "169.254.169.254")};
            }, Clock.systemUTC());
            var result = executor.execute(monitor(url, 200), UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.SUCCESS);
            assertThat(calls.get()).isEqualTo(1);
            assertThat(peer.closed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void unexpectedInternalErrorIsSeparateFromExternalFailures() {
        var executor = new HttpCheckExecutor(new DestinationPolicy(""), host -> {
            throw new IllegalStateException("secret-internal-value");
        }, Clock.systemUTC());
        var result = executor.execute(monitor("https://public.example", 200), UUID.randomUUID());
        assertThat(result.outcome()).isEqualTo(Outcome.INTERNAL_ERROR);
        assertThat(result.errorMessage()).isEqualTo("Internal execution error");
        assertThat(result.httpStatus()).isNull();
        for (var outcome : Outcome.values()) {
            assertThat(outcome.external()).isEqualTo(outcome != Outcome.CANCELLED
                    && outcome != Outcome.INTERNAL_ERROR && outcome != Outcome.DESTINATION_BLOCKED);
        }
    }

    @Test
    void tlsDoesNotTrustSelfSignedCertificates(@TempDir Path directory) throws Exception {
        Path store = directory.resolve("server.p12");
        var command = new ProcessBuilder("keytool", "-genkeypair", "-alias", "local", "-keyalg", "RSA",
                "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", "fixture-password",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "1", "-noprompt")
                .redirectErrorStream(true).start();
        command.getInputStream().readAllBytes();
        assertThat(command.waitFor()).isZero();
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = java.nio.file.Files.newInputStream(store)) {
            keys.load(input, "fixture-password".toCharArray());
        }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, "fixture-password".toCharArray());
        var ssl = SSLContext.getInstance("TLS");
        ssl.init(managers.getKeyManagers(), null, null);
        try (var server = (SSLServerSocket) ssl.getServerSocketFactory().createServerSocket(0)) {
            var closed = new CountDownLatch(1);
            Thread peer = Thread.ofVirtual().start(() -> {
                try (var socket = server.accept()) { socket.getInputStream().read(); }
                catch (IOException ignored) { }
                finally { closed.countDown(); }
            });
            String url = "https://localhost:" + server.getLocalPort();
            var result = executor(url).execute(monitor(url, 200), UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.TLS_FAILURE);
            assertThat(result.httpStatus()).isNull();
            assertThat(result.errorMessage()).isEqualTo("TLS validation or handshake failed");
            assertThat(closed.await(2, TimeUnit.SECONDS)).isTrue();
            peer.join(2000);
        }
    }

    @Test
    void tlsHandshakeAlsoHasTotalDeadline() throws Exception {
        try (var peer = new Peer(200, false)) {
            String url = peer.url().replace("http:", "https:");
            var result = executor(url).execute(monitor(url, 200), UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.TIMEOUT);
            assertThat(result.durationNanos()).isLessThan(TimeUnit.MILLISECONDS.toNanos(1500));
            assertThat(peer.closed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void trustedCertificateStillRequiresMatchingHostname(@TempDir Path directory) throws Exception {
        Path store = directory.resolve("trusted.p12");
        var command = new ProcessBuilder("keytool", "-genkeypair", "-alias", "local", "-keyalg", "RSA",
                "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", "fixture-password",
                "-dname", "CN=demo.example", "-ext", "SAN=dns:demo.example", "-validity", "1", "-noprompt")
                .redirectErrorStream(true).start();
        command.getInputStream().readAllBytes();
        assertThat(command.waitFor()).isZero();
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = java.nio.file.Files.newInputStream(store)) {
            keys.load(input, "fixture-password".toCharArray());
        }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, "fixture-password".toCharArray());
        var ssl = SSLContext.getInstance("TLS");
        ssl.init(managers.getKeyManagers(), null, null);
        var server = com.sun.net.httpserver.HttpsServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(ssl));
        server.createContext("/", exchange -> { exchange.sendResponseHeaders(204, -1); exchange.close(); });
        String previousStore = System.getProperty("javax.net.ssl.trustStore");
        String previousPassword = System.getProperty("javax.net.ssl.trustStorePassword");
        try {
            System.setProperty("javax.net.ssl.trustStore", store.toString());
            System.setProperty("javax.net.ssl.trustStorePassword", "fixture-password");
            server.start();
            String validUrl = "https://demo.example:" + server.getAddress().getPort();
            var matching = new HttpCheckExecutor(new DestinationPolicy(validUrl), host ->
                    new InetAddress[]{InetAddress.getByName("127.0.0.1")}, Clock.systemUTC());
            assertThat(matching.execute(monitor(validUrl, 204), UUID.randomUUID()).outcome())
                    .isEqualTo(Outcome.SUCCESS);
            String wrongUrl = "https://localhost:" + server.getAddress().getPort();
            var result = executor(wrongUrl).execute(monitor(wrongUrl, 204), UUID.randomUUID());
            assertThat(result.outcome()).isEqualTo(Outcome.TLS_FAILURE);
            assertThat(result.httpStatus()).isNull();
        } finally {
            server.stop(0);
            restoreProperty("javax.net.ssl.trustStore", previousStore);
            restoreProperty("javax.net.ssl.trustStorePassword", previousPassword);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) { System.clearProperty(name); }
        else { System.setProperty(name, value); }
    }

    static Monitor monitor(String url, int expected) {
        return Monitor.create(UUID.randomUUID(), MonitorConfiguration.validated("test", url, 10, 1,
                expected, 3, 2, "ops@example.org"), Instant.now());
    }

    static HttpCheckExecutor executor(String url) { return new HttpCheckExecutor(new DestinationPolicy(origin(url))); }

    static String origin(String url) {
        var uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    static final class Peer implements AutoCloseable {
        final ServerSocket server;
        final CountDownLatch accepted = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final AtomicInteger connections = new AtomicInteger();
        final AtomicReference<String> method = new AtomicReference<>();
        private final Thread thread;

        Peer(int status, boolean headers) throws IOException {
            server = new ServerSocket(0);
            thread = Thread.ofVirtual().start(() -> {
                try (var socket = server.accept()) {
                    connections.incrementAndGet();
                    accepted.countDown();
                    if (headers) {
                        var request = new StringBuilder();
                        int value;
                        while ((value = socket.getInputStream().read()) != -1) {
                            request.append((char) value);
                            if (request.toString().endsWith("\r\n\r\n")) { break; }
                        }
                        method.set(request.toString().split("\r\n")[0]);
                        // Deliberately never finish the declared body; Location is never visited.
                        socket.getOutputStream().write(("HTTP/1.1 " + status + " Fixture\r\n"
                                + "Content-Length: 999999999\r\nLocation: http://169.254.169.254/secret\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                    }
                    while (socket.getInputStream().read() != -1) { }
                    closed.countDown();
                } catch (IOException error) { closed.countDown(); }
            });
        }

        String url() { return "http://127.0.0.1:" + server.getLocalPort() + "/check"; }

        @Override
        public void close() throws Exception { server.close(); thread.interrupt(); thread.join(2000); }
    }
}
