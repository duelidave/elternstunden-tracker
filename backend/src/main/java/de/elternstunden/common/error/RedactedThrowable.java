package de.elternstunden.common.error;

/**
 * Copy of an exception chain without messages, for logging (SEC-12). Exception messages can contain personal data,
 * for example PostgreSQL constraint details with an e-mail address. Types and stack traces stay for debugging.
 */
final class RedactedThrowable extends RuntimeException {

    private static final int MAX_DEPTH = 10;

    private RedactedThrowable(String type, Throwable cause) {
        super(type + " (message redacted)", cause, false, true);
    }

    static Throwable of(Throwable original) {
        return of(original, 0);
    }

    private static Throwable of(Throwable original, int depth) {
        Throwable cause = original.getCause();
        Throwable redactedCause = cause == null || depth >= MAX_DEPTH ? null : of(cause, depth + 1);
        RedactedThrowable redacted = new RedactedThrowable(original.getClass().getName(), redactedCause);
        redacted.setStackTrace(original.getStackTrace());
        return redacted;
    }
}
