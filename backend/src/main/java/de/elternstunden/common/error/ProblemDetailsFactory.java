package de.elternstunden.common.error;

import de.elternstunden.api.model.ValidationErrorDto;
import de.elternstunden.common.web.TraceIdFilter;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds problem responses in the format of the contract schema {@code Problem} (RFC 9457, Lastenheft 8.1):
 * {@code type} is always {@code about:blank}, {@code title} the HTTP status text, plus {@code code} and
 * {@code traceId}. Validation errors never contain the rejected value.
 */
@Component
public class ProblemDetailsFactory {

    private static final URI ABOUT_BLANK = URI.create("about:blank");

    static final String CODE = "code";
    static final String TRACE_ID = "traceId";
    static final String ERRORS = "errors";

    public ProblemDetail create(ErrorCode code) {
        return create(code.status(), code, code.defaultDetail());
    }

    public ProblemDetail create(ErrorCode code, String detail) {
        return create(code.status(), code, detail);
    }

    public ProblemDetail create(HttpStatusCode status, ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        // Spring omits the default type; the contract requires it explicitly
        problem.setType(ABOUT_BLANK);
        problem.setProperty(CODE, code.name());
        problem.setProperty(TRACE_ID, TraceIdFilter.currentTraceId());
        return problem;
    }

    public ProblemDetail validation(List<ValidationErrorDto> errors) {
        ProblemDetail problem = create(ErrorCode.VALIDATION_FAILED);
        problem.setProperty(ERRORS, List.copyOf(errors));
        return problem;
    }

    /** True if the problem was already built by this factory (it carries a {@code code}). */
    static boolean isOwn(Object body) {
        return body instanceof ProblemDetail problem
                && problem.getProperties() != null
                && problem.getProperties().containsKey(CODE);
    }
}
