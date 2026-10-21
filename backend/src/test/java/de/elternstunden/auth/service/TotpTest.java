package de.elternstunden.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    /** RFC 6238 appendix B, SHA-1 key; the RFC lists 8 digits, the app uses the last 6. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924", "2000000000, 279037"})
    void matchesTheRfcTestVectors(long epochSeconds, String code) {
        assertThat(Totp.codeAt(RFC_KEY, epochSeconds / Totp.STEP_SECONDS)).isEqualTo(code);
    }

    @Test
    void acceptsOneStepOfClockSkewInBothDirections() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long step = now.getEpochSecond() / Totp.STEP_SECONDS;

        assertThat(Totp.matchingStep(RFC_KEY, Totp.codeAt(RFC_KEY, step - 1), now))
                .isEqualTo(step - 1);
        assertThat(Totp.matchingStep(RFC_KEY, Totp.codeAt(RFC_KEY, step + 1), now))
                .isEqualTo(step + 1);
        assertThat(Totp.matchingStep(RFC_KEY, Totp.codeAt(RFC_KEY, step + 2), now))
                .isEqualTo(-1);
    }

    @Test
    void rejectsMalformedCodes() {
        Instant now = Instant.ofEpochSecond(59);
        assertThat(Totp.matchingStep(RFC_KEY, null, now)).isEqualTo(-1);
        assertThat(Totp.matchingStep(RFC_KEY, "28708", now)).isEqualTo(-1);
        assertThat(Totp.matchingStep(RFC_KEY, "28708x", now)).isEqualTo(-1);
    }

    @Test
    void decodesBase32LikeAuthenticatorApps() {
        assertThat(Totp.decodeBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).isEqualTo(RFC_KEY);
        assertThat(Totp.decodeBase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")).isEqualTo(RFC_KEY);
        assertThatThrownBy(() -> Totp.decodeBase32("not-base32!")).isInstanceOf(IllegalArgumentException.class);
    }
}
