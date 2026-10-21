package de.elternstunden.auth.repo;

import static de.elternstunden.auth.repo.AppUserRepository.utc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Magic-link tokens (SEC-17): only the SHA-256 hash is stored. */
@Repository
public class UserTokenRepository {

    public static final String LOGIN = "LOGIN";

    private final JdbcClient jdbc;

    public UserTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Token row locked for redemption. */
    public record LockedToken(
            UUID id,
            UUID userId,
            String bindingHash,
            @Nullable Instant usedAt,
            Instant expiresAt) {}

    /** A new request makes all older, unused tokens of the user invalid (7.2). */
    public void deleteOpenTokens(UUID userId) {
        jdbc.sql("DELETE FROM user_token WHERE user_id = :userId AND purpose = :purpose AND used_at IS NULL")
                .param("userId", userId)
                .param("purpose", LOGIN)
                .update();
    }

    public void insert(UUID userId, String tokenHash, String bindingHash, Instant expiresAt, Instant now) {
        jdbc.sql("""
                        INSERT INTO user_token (user_id, purpose, token_hash, binding_hash, expires_at, created_at)
                        VALUES (:userId, :purpose, :tokenHash, :bindingHash, :expiresAt, :now)
                        """)
                .param("userId", userId)
                .param("purpose", LOGIN)
                .param("tokenHash", tokenHash)
                .param("bindingHash", bindingHash)
                .param("expiresAt", utc(expiresAt))
                .param("now", utc(now))
                .update();
    }

    /** Locks the token row, so parallel redemptions are serialised and only one can succeed (AC-AUTH-14). */
    public Optional<LockedToken> findByHashForUpdate(String tokenHash) {
        return jdbc.sql("""
                        SELECT id, user_id, binding_hash, used_at, expires_at FROM user_token
                        WHERE token_hash = :tokenHash AND purpose = :purpose
                        FOR UPDATE
                        """)
                .param("tokenHash", tokenHash)
                .param("purpose", LOGIN)
                .query((rs, rowNum) -> new LockedToken(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("binding_hash"),
                        instant(rs.getObject("used_at", java.time.OffsetDateTime.class)),
                        rs.getObject("expires_at", java.time.OffsetDateTime.class)
                                .toInstant()))
                .optional();
    }

    public void markUsed(UUID id, Instant now) {
        jdbc.sql("UPDATE user_token SET used_at = :now WHERE id = :id")
                .param("id", id)
                .param("now", utc(now))
                .update();
    }

    /** Cleanup (7.2): expired and redeemed tokens are deleted. */
    public int deleteExpiredOrUsed(Instant now) {
        return jdbc.sql("DELETE FROM user_token WHERE used_at IS NOT NULL OR expires_at <= :now")
                .param("now", utc(now))
                .update();
    }

    private static @Nullable Instant instant(java.time.@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
