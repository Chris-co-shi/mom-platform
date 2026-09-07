package io.github.chrisshi.mom.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestIdentityKeyResolverTest {

    @Test
    void directClientMustUseTcpRemoteAddress() throws Exception {
        RequestIdentityKeyResolver resolver = resolver(List.of());

        assertEquals("ip:10.1.1.1", resolver.resolve(exchange("10.1.1.1")).block());
    }

    @Test
    void untrustedClientMustNotSpoofForwardedAddress() throws Exception {
        RequestIdentityKeyResolver resolver = resolver(List.of("192.168.3.10/32"));

        assertEquals("ip:10.1.1.1", resolver.resolve(
                exchange("10.1.1.1", "X-Forwarded-For", "8.8.8.8")).block());
    }

    @Test
    void trustedProxyMustUseSingleForwardedIpv4() throws Exception {
        RequestIdentityKeyResolver resolver = resolver(List.of("192.168.3.10/32"));

        assertEquals("ip:10.20.30.40", resolver.resolve(
                exchange("192.168.3.10", "X-Forwarded-For", "10.20.30.40")).block());
    }

    @Test
    void clientsBehindSameTrustedProxyMustUseDifferentBuckets() throws Exception {
        RequestIdentityKeyResolver resolver = resolver(List.of("192.168.3.0/24"));

        String first = resolver.resolve(
                exchange("192.168.3.10", "X-Forwarded-For", "10.20.30.40")).block();
        String second = resolver.resolve(
                exchange("192.168.3.10", "X-Forwarded-For", "10.20.30.41")).block();

        assertNotEquals(first, second);
    }

    @Test
    void trustedProxyMustFallbackForMissingBlankInvalidOrMultipleForwardedValues() throws Exception {
        RequestIdentityKeyResolver resolver = resolver(List.of("192.168.3.10/32"));

        assertEquals("ip:192.168.3.10", resolver.resolve(exchange("192.168.3.10")).block());
        assertEquals("ip:192.168.3.10", resolver.resolve(
                exchange("192.168.3.10", "X-Forwarded-For", " ")).block());
        assertEquals("ip:192.168.3.10", resolver.resolve(
                exchange("192.168.3.10", "X-Forwarded-For", "not-an-ip")).block());
        assertEquals("ip:192.168.3.10", resolver.resolve(
                exchange("192.168.3.10", "X-Forwarded-For", "10.20.30.40, 10.20.30.41")).block());
        assertEquals("ip:192.168.3.10", resolver.resolve(
                exchange("192.168.3.10", "X-Forwarded-For", "10.20.30.40", "10.20.30.41")).block());
    }

    @Test
    void missingRemoteAddressMustUseStableUnknownBucket() {
        RequestIdentityKeyResolver resolver = resolver(List.of());

        assertEquals("ip:unknown", resolver.resolve(MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/login").build())).block());
    }

    @Test
    void directIpv6ClientMustUseTcpAddressWithoutTrustingForwardedHeader() throws Exception {
        RequestIdentityKeyResolver resolver = resolver(List.of("192.168.3.10/32"));

        assertEquals("ip:2001:db8:0:0:0:0:0:1", resolver.resolve(
                exchange("2001:db8::1", "X-Forwarded-For", "8.8.8.8")).block());
    }

    @Test
    void invalidTrustedProxyCidrMustFailFast() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver(List.of("192.168.3.999/32")));
        assertThrows(IllegalArgumentException.class,
                () -> resolver(List.of("2001:db8::/32")));
    }

    private static RequestIdentityKeyResolver resolver(List<String> trustedProxies) {
        TrustedClientIpProperties properties = new TrustedClientIpProperties(trustedProxies);
        return new RequestIdentityKeyResolver(new TrustedClientIpResolver(properties));
    }

    private static MockServerWebExchange exchange(String remoteAddress, String... header) throws Exception {
        MockServerHttpRequest.BaseBuilder<?> request = MockServerHttpRequest.get("/auth/login")
                .remoteAddress(new InetSocketAddress(InetAddress.getByName(remoteAddress), 12345));
        if (header.length > 0) {
            request.header(header[0], java.util.Arrays.copyOfRange(header, 1, header.length));
        }
        return MockServerWebExchange.from(request.build());
    }
}
