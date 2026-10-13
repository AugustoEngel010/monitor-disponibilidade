package br.com.monitor.monitoring;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import br.com.monitor.administration.Monitor;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import static br.com.monitor.monitoring.CheckResult.Outcome;

public final class HttpCheckExecutor {
    private final DestinationPolicy policy;
    private final Resolver resolver;
    private final Clock clock;

    public HttpCheckExecutor(DestinationPolicy policy) { this(policy, InetAddress::getAllByName, Clock.systemUTC()); }

    public HttpCheckExecutor(DestinationPolicy policy, Resolver resolver, Clock clock) {
        this.policy = policy;
        this.resolver = resolver;
        this.clock = clock;
    }

    public CheckResult execute(Monitor monitor, UUID executionId) {
        Instant started = clock.instant();
        long tick = System.nanoTime();
        long budget = TimeUnit.SECONDS.toNanos(monitor.configuration().timeoutSeconds());
        if (Thread.currentThread().isInterrupted()) {
            return result(monitor, executionId, started, tick, Outcome.CANCELLED, null);
        }
        var stopped = new AtomicBoolean();
        var request = new AtomicReference<HttpGet>();
        var client = new AtomicReference<CloseableHttpClient>();
        var task = new FutureTask<CheckResult>(() -> attempt(monitor, executionId, started, tick,
                budget, stopped, request, client));
        Thread worker = Thread.ofVirtual().name("http-check-" + executionId).start(task);
        try {
            long remaining = budget - (System.nanoTime() - tick);
            if (remaining <= 0) { throw new TimeoutException(); }
            return task.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            return result(monitor, executionId, started, tick, Outcome.TIMEOUT, null);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return result(monitor, executionId, started, tick, Outcome.CANCELLED, null);
        } catch (ExecutionException error) {
            return result(monitor, executionId, started, tick, classify(error.getCause()), null);
        } finally {
            stopped.set(true);
            if (request.get() != null) { request.get().cancel(); }
            if (client.get() != null) { client.get().close(CloseMode.IMMEDIATE); }
            task.cancel(true);
            worker.interrupt();
        }
    }

    private CheckResult attempt(Monitor monitor, UUID id, Instant started, long tick, long budget,
            AtomicBoolean stopped, AtomicReference<HttpGet> requestRef,
            AtomicReference<CloseableHttpClient> clientRef) throws Exception {
        URI uri;
        try { uri = URI.create(monitor.configuration().url()); }
        catch (IllegalArgumentException error) { throw new DestinationPolicy.Blocked(); }
        policy.validate(uri);
        String host = DestinationPolicy.host(uri);
        InetAddress[] addresses = policy.validateAddresses(uri, resolver.resolve(host));
        checkDeadline(stopped, tick, budget);
        DnsResolver pinned = new DnsResolver() {
            @Override
            public InetAddress[] resolve(String name) throws UnknownHostException {
                if (!name.equalsIgnoreCase(host)) { throw new DestinationPolicy.Blocked(); }
                // One attempt uses one validated peer; address fallback must not hide a TLS failure.
                return new InetAddress[]{policy.validateAddresses(uri, addresses)[0]};
            }
            @Override
            public String resolveCanonicalHostname(String name) { return name; }
        };
        var timeout = Timeout.ofNanoseconds(Math.max(1, budget - (System.nanoTime() - tick)));
        var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(pinned)
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(timeout)
                        .setSocketTimeout(timeout).build()).build();
        var client = HttpClients.custom().setConnectionManager(manager).disableRedirectHandling()
                .disableAutomaticRetries().disableCookieManagement().disableAuthCaching()
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(timeout)
                        .setConnectionRequestTimeout(timeout).build()).build();
        clientRef.set(client);
        var request = new HttpGet(uri);
        request.setHeader("Connection", "close");
        requestRef.set(request);
        try {
            checkDeadline(stopped, tick, budget);
            var response = client.executeOpen(null, request, null);
            try {
                int status = response.getCode();
                var observed = result(monitor, id, started, tick,
                        status == monitor.configuration().expectedStatus() ? Outcome.SUCCESS : Outcome.HTTP_FAILURE,
                        status);
                // Close the socket before closing the entity: never drain or wait for a response body.
                client.close(CloseMode.IMMEDIATE);
                return observed;
            } finally {
                client.close(CloseMode.IMMEDIATE);
                // Entity cleanup after hard socket close may throw; headers already define the observation.
                try { response.close(); }
                catch (IOException ignored) { }
            }
        } finally {
            request.cancel();
            client.close(CloseMode.IMMEDIATE);
        }
    }

    private static void checkDeadline(AtomicBoolean stopped, long tick, long budget) throws Exception {
        if (stopped.get() || Thread.currentThread().isInterrupted()) { throw new InterruptedException(); }
        if (System.nanoTime() - tick >= budget) { throw new TimeoutException(); }
    }

    private CheckResult result(Monitor monitor, UUID id, Instant start, long tick, Outcome outcome, Integer status) {
        return new CheckResult(monitor.id(), id, monitor.state().configurationVersion(),
                monitor.state().executionGeneration(), start, clock.instant(),
                Math.max(0, System.nanoTime() - tick), outcome, status);
    }

    private static Outcome classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof DestinationPolicy.Blocked) { return Outcome.DESTINATION_BLOCKED; }
            if (cause instanceof UnknownHostException) { return Outcome.DNS_FAILURE; }
            if (cause instanceof SocketTimeoutException || cause instanceof TimeoutException) {
                return Outcome.TIMEOUT;
            }
            if (cause instanceof InterruptedException) { return Outcome.CANCELLED; }
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLException) { return Outcome.TLS_FAILURE; }
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException) { return Outcome.CONNECTION_FAILURE; }
        }
        return Outcome.INTERNAL_ERROR;
    }

    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
