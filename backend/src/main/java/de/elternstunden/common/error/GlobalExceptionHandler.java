package de.elternstunden.common.error;

import de.elternstunden.api.model.ValidationErrorDto;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Turns every error under {@code /api} into {@code application/problem+json} (Lastenheft 8.1). Security errors
 * arrive here too, the security handlers delegate to this class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailsFactory problems;

    public GlobalExceptionHandler(ProblemDetailsFactory problems) {
        this.problems = problems;
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex) {
        ProblemDetail problem = problems.create(ex.code(), ex.getMessage());
        return ResponseEntity.status(problem.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail problem = problems.create(ex.code(), ex.getMessage());
        ex.properties().forEach(problem::setProperty);
        return respond(problem);
    }

    /**
     * Constraints on query and path parameters: the generated interfaces are {@code @Validated}, so they arrive as
     * method validation, e.g. a too short search (AC-OV-04) or an unknown sort field (AC-OV-03).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<ValidationErrorDto> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ValidationErrorDto(
                        parameterName(violation.getPropertyPath()),
                        violation
                                .getConstraintDescriptor()
                                .getAnnotation()
                                .annotationType()
                                .getSimpleName(),
                        violation.getMessage()))
                .toList();
        return respond(problems.validation(errors));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(problems.create(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(CsrfException.class)
    ResponseEntity<ProblemDetail> handleCsrf(CsrfException ex) {
        return respond(problems.create(ErrorCode.CSRF_INVALID));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(problems.create(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        // Messages may contain personal data (e.g. constraint details), so only types and stack traces are logged
        log.error("Unhandled exception, responding with 500", RedactedThrowable.of(ex));
        return respond(problems.create(ErrorCode.INTERNAL_ERROR));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationErrorDto> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.add(fieldError(error)));
        ex.getBindingResult().getGlobalErrors().forEach(error -> errors.add(objectError(error)));
        return handleExceptionInternal(ex, problems.validation(errors), headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationErrorDto> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(error -> errors.add(fieldError(error)));
            } else {
                String parameter = result.getMethodParameter().getParameterName();
                result.getResolvableErrors()
                        .forEach(error -> errors.add(error(parameter == null ? "parameter" : parameter, error)));
            }
        });
        return handleExceptionInternal(ex, problems.validation(errors), headers, status, request);
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Unknown fields are a validation error (mass assignment protection, Lastenheft 8.1)
        if (ex.getCause() instanceof UnrecognizedPropertyException unknown) {
            ValidationErrorDto error =
                    new ValidationErrorDto(unknown.getPropertyName(), "Unknown", "unknown or read-only field");
            return handleExceptionInternal(ex, problems.validation(List.of(error)), headers, status, request);
        }
        return handleExceptionInternal(ex, problems.create(ErrorCode.MALFORMED_REQUEST), headers, status, request);
    }

    private static String parameterName(Path path) {
        String name = "parameter";
        for (Path.Node node : path) {
            if (node.getName() != null) {
                name = node.getName();
            }
        }
        return name;
    }

    /** Replaces Spring's default problem bodies with ours, so every error carries {@code code} and {@code traceId}. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        Object problem = ProblemDetailsFactory.isOwn(body)
                ? body
                : problems.create(
                        statusCode,
                        ErrorCode.forStatus(statusCode),
                        ErrorCode.forStatus(statusCode).defaultDetail());
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static ValidationErrorDto fieldError(FieldError error) {
        return error(error.getField(), error);
    }

    private static ValidationErrorDto objectError(ObjectError error) {
        return error(error.getObjectName(), error);
    }

    private static ValidationErrorDto error(String field, MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String code = codes == null || codes.length == 0 ? "Invalid" : codes[codes.length - 1];
        String message = error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage();
        return new ValidationErrorDto(field, code, message);
    }
}
