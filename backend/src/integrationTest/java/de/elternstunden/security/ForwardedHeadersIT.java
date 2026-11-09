package de.elternstunden.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/** Forwarded headers (SEC-04, 9.1): the proxy header is only read from a proxy that is configured as trusted. */
class ForwardedHeadersIT extends IntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    void withoutTrustedProxyRegexNoOneIsTrusted() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("native");

        String trusted = environment.getProperty("server.tomcat.remoteip.internal-proxies");
        for (String address : new String[] {"127.0.0.1", "::1", "10.0.0.5", "172.18.0.2", "192.168.1.1"}) {
            assertThat(address).as(address).doesNotMatch(trusted);
        }
    }
}
