package de.elternstunden.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpTest {

    @Test
    void ipv4IsKeyedByTheFullAddressAndLoggedAsSlash24() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.77");

        assertThat(ClientIp.rateLimitKey(request)).isEqualTo("203.0.113.77/32");
        assertThat(ClientIp.forLogs(request)).isEqualTo("203.0.113.0/24");
    }

    @Test
    void ipv6IsKeyedBySlash64AndLoggedAsSlash48() {
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.setRemoteAddr("2001:db8:1234:5678:aaaa::1");
        MockHttpServletRequest second = new MockHttpServletRequest();
        second.setRemoteAddr("2001:db8:1234:5678:ffff::2");

        assertThat(ClientIp.rateLimitKey(first)).isEqualTo(ClientIp.rateLimitKey(second));
        assertThat(ClientIp.rateLimitKey(first)).isEqualTo("2001:db8:1234:5678:0:0:0:0/64");
        assertThat(ClientIp.forLogs(first)).isEqualTo("2001:db8:1234:0:0:0:0:0/48");
    }

    @Test
    void ignoresForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(ClientIp.rateLimitKey(request)).isEqualTo("198.51.100.1/32");
    }

    @Test
    void unparsableAddressesShareOneKey() {
        assertThat(ClientIp.prefix("not-an-ip", 32, 64)).isEqualTo("unknown");
    }
}
