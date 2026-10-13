package br.com.monitor;

import java.net.InetAddress;
import java.net.URI;
import br.com.monitor.monitoring.DestinationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinationPolicyTest {
    private final DestinationPolicy policy = new DestinationPolicy("");

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "0.1.2.3", "10.0.0.1", "100.64.0.1", "100.127.255.255",
        "127.0.0.1", "169.254.169.254", "172.16.0.1", "172.31.255.255", "192.168.0.1", "192.0.0.1",
        "192.0.2.1", "192.88.99.1", "198.18.0.1", "198.19.255.255", "198.51.100.1", "203.0.113.1",
        "224.0.0.1", "239.0.0.1", "240.0.0.1", "255.255.255.255", "::", "::1", "fc00::1", "fd00::1",
        "fe80::1", "ff02::1", "::ffff:127.0.0.1", "64:ff9b::7f00:1", "2001::1", "2001:db8::1",
        "2002:7f00:1::1", "3fff::1"})
    void rejectsNonPublicAddresses(String address) throws Exception {
        // MON-05-AC05: no non-public peer may be connected.
        assertThatThrownBy(() -> policy.validateAddresses(URI.create("https://public.example"),
                new InetAddress[]{InetAddress.getByName(address)}))
                .isInstanceOf(DestinationPolicy.Blocked.class).hasMessage("Destination blocked by policy");
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1", "100.63.255.255", "100.128.0.1", "172.15.255.255",
        "172.32.0.1", "192.169.0.1", "2606:4700:4700::1111", "2001:4860:4860::8888"})
    void permitsPublicAddresses(String address) throws Exception {
        var addresses = new InetAddress[]{InetAddress.getByName(address)};
        assertThat(policy.validateAddresses(URI.create("https://public.example"), addresses))
                .containsExactlyElementsOf(java.util.List.of(addresses));
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/passwd", "http://user:secret@example.org", "http://example.org?token=x",
        "http://example.org#secret", "http://example.org:0", "http://example.org:65536", "http:///missing",
        "http://127.0.0.1", "http://[::1]", "http://169.254.169.254", "http://2130706433", "http://127.1"})
    void rejectsInvalidUrlsAndBlockedLiteralsBeforeDns(String url) {
        assertThatThrownBy(() -> policy.validate(URI.create(url))).isInstanceOf(DestinationPolicy.Blocked.class);
    }

    @Test
    void rejectsMixedDnsAndEmptyAnswers() throws Exception {
        var uri = URI.create("https://public.example");
        assertThatThrownBy(() -> policy.validateAddresses(uri,
                new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("127.0.0.1")}))
                .isInstanceOf(DestinationPolicy.Blocked.class);
        assertThatThrownBy(() -> policy.validateAddresses(uri, new InetAddress[0]))
                .isInstanceOf(java.net.UnknownHostException.class);
    }

    @Test
    void demoExceptionIsOptInAndLimitedToExactOrigin() throws Exception {
        // MON-05-AC06: host/scheme/port are all part of the exception.
        var demo = new DestinationPolicy("http://demo:8080");
        var privateAddress = new InetAddress[]{InetAddress.getByName("172.20.0.5")};
        assertThat(demo.validateAddresses(URI.create("http://demo:8080/health"), privateAddress))
                .containsExactly(privateAddress);
        for (var url : new String[]{"http://other:8080", "http://demo:8081", "https://demo:8080",
                "http://172.20.0.5:8080", "http://demo.attacker:8080"}) {
            assertThatThrownBy(() -> demo.validateAddresses(URI.create(url), privateAddress))
                    .isInstanceOf(DestinationPolicy.Blocked.class);
        }
        assertThatThrownBy(() -> policy.validateAddresses(URI.create("http://demo:8080"), privateAddress))
                .isInstanceOf(DestinationPolicy.Blocked.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "http://demo:8080/path", "http://demo?secret=x", "http://user:pass@demo",
        "http://demo#x", "http://demo:0", "http://demo:65536", "file:///demo"})
    void rejectsMalformedDemoConfigurationWithoutEchoingIt(String value) {
        assertThatThrownBy(() -> new DestinationPolicy(value)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid demonstration origin");
    }
}
