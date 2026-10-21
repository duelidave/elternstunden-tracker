package de.elternstunden.auth.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.crypto.Hashing;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Master bootstrap (7.9, D-22): the configuration is the source of truth. On every start the master account is
 * created or updated from it. In production a missing e-mail, password hash or TOTP secret, or a hash below the
 * Argon2id minimum, stops the start (fail fast, AC-MASTER-01, AC-MASTER-07). The TOTP secret is never stored in the DB.
 */
@Component
public class MasterAccountSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterAccountSync.class);

    private final AppProperties.Master master;
    private final AppUserRepository users;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final Environment environment;
    private final Clock clock;

    public MasterAccountSync(
            AppProperties properties,
            AppUserRepository users,
            AuditLog audit,
            TransactionTemplate tx,
            Environment environment,
            Clock clock) {
        this.master = properties.master();
        this.users = users;
        this.audit = audit;
        this.tx = tx;
        this.environment = environment;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        if (production) {
            requireCompleteConfiguration();
        }
        if (!master.configured()) {
            log.info("No master admin configured, skipping master bootstrap");
            return;
        }
        String hash = require(master.passwordHash(), "MASTER_ADMIN_PASSWORD_HASH");
        if (!MasterPassword.meetsMinimum(hash)) {
            if (production) {
                throw new IllegalStateException(
                        "MASTER_ADMIN_PASSWORD_HASH must be Argon2id with at least m=19456, t=2, p=1");
            }
            log.warn("Master password hash is below the Argon2id minimum (allowed outside production only)");
        }
        String email = Hashing.normalizeEmail(require(master.email(), "MASTER_ADMIN_EMAIL"));
        tx.executeWithoutResult(status -> sync(email, hash));
    }

    private void sync(String email, String hash) {
        Optional<AppUser> sameEmail = users.findByEmailForUpdate(email);
        if (sameEmail.isPresent() && !sameEmail.get().managedByConfig()) {
            throw new IllegalStateException(
                    "MASTER_ADMIN_EMAIL belongs to a regular account; resolve the conflict manually (7.9)");
        }
        UUID id = users.upsertMaster(email, master.displayName(), hash, clock.instant());
        audit.system("MASTER_CREDENTIALS_SYNCED", "app_user", id, Map.of());
        log.info("Master admin synchronised from configuration");
    }

    private void requireCompleteConfiguration() {
        require(master.email(), "MASTER_ADMIN_EMAIL");
        require(master.passwordHash(), "MASTER_ADMIN_PASSWORD_HASH");
        require(master.totpSecret(), "MASTER_ADMIN_TOTP_SECRET");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required (Lastenheft 7.9)");
        }
        return value;
    }
}
