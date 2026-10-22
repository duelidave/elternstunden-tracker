package de.elternstunden.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Stable, machine-readable error codes of the API (Lastenheft 8.1). The UI maps them to its own texts, so the
 * detail texts here stay short, technical and free of personal data.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    CAPTCHA_INVALID(HttpStatus.BAD_REQUEST, "Captcha missing, invalid, expired or already used."),
    LINK_INVALID(HttpStatus.BAD_REQUEST, "The link is expired or was already used."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "Login failed."),
    OTHER_DEVICE_CONFIRMATION_REQUIRED(HttpStatus.CONFLICT, "Link was requested on another device."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request could not be read."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "CSRF token missing or invalid."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Requested media type not available."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Request body too large."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type not supported."),
    PAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "Page size too large."),
    ALREADY_ASSIGNED(HttpStatus.CONFLICT, "The account is already assigned to a family."),
    CLAIM_CORRECTION_LIMIT(HttpStatus.CONFLICT, "No corrections of the child data left."),
    SCHOOL_YEAR_OVERLAP(HttpStatus.CONFLICT, "School years must not overlap."),
    SCHOOL_YEAR_LABEL_TAKEN(HttpStatus.CONFLICT, "School year label already exists."),
    SCHOOL_YEAR_CLOSED(HttpStatus.CONFLICT, "The school year is closed."),
    ENTRIES_OUTSIDE_RANGE(HttpStatus.CONFLICT, "Existing entries would fall outside the school year."),
    EXTERNAL_REF_TAKEN(HttpStatus.CONFLICT, "External reference already in use."),
    ACCOUNT_MANAGED_BY_CONFIG(HttpStatus.CONFLICT, "The master account is managed by the configuration."),
    OWN_ACCOUNT(HttpStatus.CONFLICT, "Not allowed on the own account."),
    USER_ANONYMIZED(HttpStatus.CONFLICT, "The account is anonymized."),
    MFA_INVALID(HttpStatus.BAD_REQUEST, "Second factor invalid."),
    REAUTH_REQUIRED(HttpStatus.FORBIDDEN, "A fresh login is required."),
    TOTP_REQUIRED(HttpStatus.CONFLICT, "TOTP is required for admins."),
    TOTP_SETUP_MISSING(HttpStatus.CONFLICT, "No TOTP setup to confirm."),
    TOTP_ALREADY_ENABLED(HttpStatus.CONFLICT, "TOTP is already enabled."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests."),
    REQUEST_REJECTED(HttpStatus.BAD_REQUEST, "Request rejected."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error.");

    private final HttpStatus status;
    private final String defaultDetail;

    ErrorCode(HttpStatus status, String defaultDetail) {
        this.status = status;
        this.defaultDetail = defaultDetail;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultDetail() {
        return defaultDetail;
    }

    /** Fallback code for errors that only carry an HTTP status, e.g. framework exceptions. */
    public static ErrorCode forStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMITED;
            default -> status.is5xxServerError() ? INTERNAL_ERROR : REQUEST_REJECTED;
        };
    }
}
