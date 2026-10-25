package de.elternstunden.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 9.1 "Request-Größe": bodies under /api are at most 64 KB, the CSV import at most 1 MB; bodies without a length
 * (chunked) are refused. Checked before anything reads the body, so large bodies cannot fill the memory (API4).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeFilter extends OncePerRequestFilter {

    static final long MAX_JSON_BYTES = 64 * 1024;
    static final long MAX_IMPORT_BYTES = 1024 * 1024;
    private static final String IMPORT_PATH = ApiPaths.BASE + "/admin/children/import";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ApiPaths.BASE + "/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        long max = request.getRequestURI().equals(IMPORT_PATH) ? MAX_IMPORT_BYTES : MAX_JSON_BYTES;
        boolean chunked = length < 0 && request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null;
        if (length > max || chunked) {
            // Rendered as problem+json (PAYLOAD_TOO_LARGE) by the error controller
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
