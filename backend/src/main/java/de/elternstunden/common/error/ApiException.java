package de.elternstunden.common.error;

import java.util.Map;

/**
 * Exception for expected API errors. The message ends up in the problem's {@code detail} and additional
 * {@code properties} become extra problem fields; neither MAY contain personal data (SEC-12), except values the
 * contract explicitly allows (e.g. the masked e-mail of OTHER_DEVICE_CONFIRMATION_REQUIRED).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties;

    public ApiException(ErrorCode code) {
        this(code, code.defaultDetail(), Map.of());
    }

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public ApiException(ErrorCode code, Map<String, Object> properties) {
        this(code, code.defaultDetail(), properties);
    }

    public ApiException(ErrorCode code, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.properties = Map.copyOf(properties);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
