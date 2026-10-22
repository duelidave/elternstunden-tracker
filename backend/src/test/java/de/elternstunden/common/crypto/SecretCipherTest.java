package de.elternstunden.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.elternstunden.common.config.TestProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** AES-GCM for secrets at rest (SEC-05). */
class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher(TestProperties.defaults());

    @Test
    void roundTripsWithAFreshIvEachTime() {
        byte[] secret = "totp-secret-bytes".getBytes(StandardCharsets.UTF_8);

        byte[] first = cipher.encrypt(secret);
        byte[] second = cipher.encrypt(secret);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(secret);
        assertThat(cipher.decrypt(second)).isEqualTo(secret);
    }

    @Test
    void detectsTampering() {
        byte[] stored = cipher.encrypt(new byte[] {1, 2, 3});
        stored[stored.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }
}
