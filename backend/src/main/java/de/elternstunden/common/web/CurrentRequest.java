package de.elternstunden.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Access to the servlet request and response inside controllers that implement generated API interfaces (D-25),
 * whose method signatures cannot take them as parameters.
 */
public final class CurrentRequest {

    private CurrentRequest() {}

    public static HttpServletRequest request() {
        return attributes().getRequest();
    }

    public static HttpServletResponse response() {
        HttpServletResponse response = attributes().getResponse();
        if (response == null) {
            throw new IllegalStateException("No current HTTP response");
        }
        return response;
    }

    private static ServletRequestAttributes attributes() {
        return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    }
}
