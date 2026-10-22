package de.elternstunden.common.error;

import de.elternstunden.api.model.ValidationErrorDto;
import java.util.List;
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

    /** 400 VALIDATION_FAILED for rules beyond the contract's schema; the messages never contain the value. */
    public static ApiException validation(List<ValidationErrorDto> errors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("errors", List.copyOf(errors)));
    }

    public static ApiException validation(String field, String code, String message) {
        return validation(List.of(new ValidationErrorDto(field, code, message)));
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
