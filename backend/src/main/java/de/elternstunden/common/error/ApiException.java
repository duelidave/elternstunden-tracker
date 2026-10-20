package de.elternstunden.common.error;

/**
 * Exception for expected API errors. The message ends up in the problem's {@code detail} and therefore MUST NOT
 * contain personal data (SEC-12).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code) {
        this(code, code.defaultDetail());
    }

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
