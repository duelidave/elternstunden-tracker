package de.elternstunden.auth.repo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Admin TOTP: the encrypted secret on {@code app_user} and the recovery codes (SEC-05). */
@Repository
public class TotpRepository {

    /** The stored (encrypted) secret and whether it is confirmed. */
    public static final class StoredSecret {

        private final byte[] encrypted;
        private final boolean enabled;

        StoredSecret(byte[] encrypted, boolean enabled) {
            this.encrypted = encrypted.clone();
            this.enabled = enabled;
        }

        public byte[] encrypted() {
            return encrypted.clone();
        }

        public boolean enabled() {
            return enabled;
        }
    }

    private final JdbcClient jdbc;

    public TotpRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredSecret> secret(UUID userId) {
        return jdbc.sql(
                        "SELECT totp_secret_enc, totp_enabled_at FROM app_user WHERE id = :id AND totp_secret_enc IS NOT NULL")
                .param("id", userId)
                .query((rs, rowNum) -> new StoredSecret(rs.getBytes(1), rs.getObject(2) != null))
                .optional();
    }

    public boolean enabled(UUID userId) {
        return jdbc.sql("SELECT totp_enabled_at IS NOT NULL FROM app_user WHERE id = :id")
                .param("id", userId)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    /** A new, not yet confirmed secret; replaces an earlier unconfirmed one. */
    public void storePending(UUID userId, byte[] encrypted, Instant now) {
        jdbc.sql("""
                        UPDATE app_user SET totp_secret_enc = :secret, totp_enabled_at = NULL, updated_at = :now
                        WHERE id = :id
                        """)
                .param("id", userId)
                .param("secret", encrypted)
                .param("now", utc(now))
                .update();
    }

    public void enable(UUID userId, Instant now) {
        jdbc.sql("UPDATE app_user SET totp_enabled_at = :now, updated_at = :now WHERE id = :id")
                .param("id", userId)
                .param("now", utc(now))
                .update();
    }

    /** Removes secret and recovery codes. */
    public void clear(UUID userId, Instant now) {
        jdbc.sql("""
                        UPDATE app_user SET totp_secret_enc = NULL, totp_enabled_at = NULL, updated_at = :now
                        WHERE id = :id
                        """).param("id", userId).param("now", utc(now)).update();
        jdbc.sql("DELETE FROM user_recovery_code WHERE user_id = :id")
                .param("id", userId)
                .update();
    }

    public void replaceRecoveryCodes(UUID userId, List<String> codeHashes, Instant now) {
        jdbc.sql("DELETE FROM user_recovery_code WHERE user_id = :id")
                .param("id", userId)
                .update();
        for (String hash : codeHashes) {
            jdbc.sql("INSERT INTO user_recovery_code (user_id, code_hash, created_at) VALUES (:id, :hash, :now)")
                    .param("id", userId)
                    .param("hash", hash)
                    .param("now", utc(now))
                    .update();
        }
    }

    /** Marks an unused code as used; true if there was one (each code works once). */
    public boolean useRecoveryCode(UUID userId, String codeHash, Instant now) {
        return jdbc.sql("""
                        UPDATE user_recovery_code SET used_at = :now
                        WHERE id = (SELECT id FROM user_recovery_code
                                    WHERE user_id = :id AND code_hash = :hash AND used_at IS NULL
                                    LIMIT 1 FOR UPDATE)
                        """)
                        .param("id", userId)
                        .param("hash", codeHash)
                        .param("now", utc(now))
                        .update()
                > 0;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
