package de.elternstunden.common.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StartupChecksTest {

    @Test
    void productionNeedsStrongKeys() {
        assertThatThrownBy(() -> StartupChecks.requireStrong("APP_ENCRYPTION_KEY", "kurz"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ENCRYPTION_KEY");
        assertThatThrownBy(() -> StartupChecks.requireStrong("ALTCHA_HMAC_KEY", "local-dev-altcha-key"))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> StartupChecks.requireStrong("APP_ENCRYPTION_KEY", "only-for-tests-".repeat(3)))
                .doesNotThrowAnyException();
    }
}
