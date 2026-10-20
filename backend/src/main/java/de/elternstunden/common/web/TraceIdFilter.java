package de.elternstunden.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a trace id (NFR-06, SEC-12): in the MDC for the JSON logs, in the {@code X-Trace-Id} response
 * header and in every problem response. Ids from the client are ignored, so they cannot pollute the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // An error dispatch keeps the id of the original request
        String traceId = request.getAttribute(ATTRIBUTE) instanceof String existing ? existing : newTraceId();
        request.setAttribute(ATTRIBUTE, traceId);
        response.setHeader(HEADER, traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /** The trace id of the current request, or {@code "none"} outside of a request. */
    public static String currentTraceId() {
        String fromMdc = MDC.get(MDC_KEY);
        if (fromMdc != null) {
            return fromMdc;
        }
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes != null
                && attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST) instanceof String traceId) {
            return traceId;
        }
        return "none";
    }

    static String newTraceId() {
        byte[] bytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
