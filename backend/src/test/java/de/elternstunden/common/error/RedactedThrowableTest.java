package de.elternstunden.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class RedactedThrowableTest {

    @Test
    void removesMessagesButKeepsTypesAndStackTraces() {
        IllegalStateException cause = new IllegalStateException("Key (email)=(anna@example.org) already exists");
        RuntimeException original = new RuntimeException("child Anna Müller", cause);

        Throwable redacted = RedactedThrowable.of(original);

        String printed = print(redacted);
        assertThat(printed)
                .doesNotContain("anna@example.org", "Anna Müller")
                .contains(RuntimeException.class.getName(), IllegalStateException.class.getName());
        assertThat(redacted.getStackTrace()).isEqualTo(original.getStackTrace());
        assertThat(redacted.getCause()).isNotNull();
        assertThat(redacted.getCause().getStackTrace()).isEqualTo(cause.getStackTrace());
    }

    private static String print(Throwable throwable) {
        StringWriter out = new StringWriter();
        throwable.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}
