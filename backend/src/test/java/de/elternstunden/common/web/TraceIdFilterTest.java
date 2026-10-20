package de.elternstunden.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void setsTraceIdInHeaderAndMdcAndCleansUp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInChain.set(MDC.get(TraceIdFilter.MDC_KEY));
            }
        });

        String header = response.getHeader(TraceIdFilter.HEADER);
        assertThat(header).matches("[0-9a-f]{32}");
        assertThat(seenInChain.get()).isEqualTo(header);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void ignoresTraceIdSentByTheClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/status");
        request.addHeader(TraceIdFilter.HEADER, "attacker-controlled\nfake log line");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(TraceIdFilter.HEADER)).matches("[0-9a-f]{32}");
    }

    @Test
    void errorDispatchKeepsTheTraceIdOfTheOriginalRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        String original = response.getHeader(TraceIdFilter.HEADER);

        request.setDispatcherType(DispatcherType.ERROR);
        MockHttpServletResponse errorResponse = new MockHttpServletResponse();
        filter.doFilter(request, errorResponse, new MockFilterChain());

        assertThat(errorResponse.getHeader(TraceIdFilter.HEADER)).isEqualTo(original);
    }

    @Test
    void currentTraceIdOutsideOfARequestIsNone() {
        assertThat(TraceIdFilter.currentTraceId()).isEqualTo("none");
    }
}
