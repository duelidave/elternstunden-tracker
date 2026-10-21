package de.elternstunden.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.config.TestProperties;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

/** Fail fast in production (7.9, AC-MASTER-01, AC-MASTER-07). */
class MasterAccountSyncTest {

    private static final String STRONG_HASH = MasterPassword.encoder().encode("master-password");
    private static final String WEAK_HASH = "$argon2id$v=19$m=4096,t=1,p=1$c2FsdHNhbHQ$aGFzaGhhc2hoYXNo";

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);

    @Test
    void productionWithoutEmailStops() {
        assertThatThrownBy(() -> sync(master(null, STRONG_HASH, "GEZDGNBV"), "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_ADMIN_EMAIL");
    }

    @Test
    void productionWithoutPasswordHashStops() {
        assertThatThrownBy(() -> sync(master("master@example.test", null, "GEZDGNBV"), "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_ADMIN_PASSWORD_HASH");
    }

    @Test
    void productionWithoutTotpSecretStops() {
        assertThatThrownBy(() -> sync(master("master@example.test", STRONG_HASH, null), "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_ADMIN_TOTP_SECRET");
    }

    @Test
    void productionWithAWeakHashStops() {
        assertThatThrownBy(() -> sync(master("master@example.test", WEAK_HASH, "GEZDGNBV"), "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Argon2id");
    }

    @Test
    void developmentWithoutMasterStartsWithoutOne() {
        assertThatCode(() -> sync(master(null, null, null), "dev")).doesNotThrowAnyException();
        verifyNoInteractions(users, tx);
    }

    @Test
    void developmentAcceptsAWeakHash() {
        assertThatCode(() -> sync(master("master@example.test", WEAK_HASH, "GEZDGNBV"), "dev"))
                .doesNotThrowAnyException();
    }

    private void sync(AppProperties.Master master, String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        new MasterAccountSync(
                        TestProperties.withMaster(master),
                        users,
                        mock(AuditLog.class),
                        tx,
                        environment,
                        Clock.systemUTC())
                .run(new DefaultApplicationArguments());
    }

    private static AppProperties.Master master(String email, String hash, String totp) {
        return new AppProperties.Master(email, hash, totp, "Master-Admin");
    }
}
