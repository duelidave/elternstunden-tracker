package de.elternstunden.auth.repo;

import static de.elternstunden.auth.repo.AppUserRepository.utc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Auth outbox (7.2, 7.11): anonymous requests write one row synchronously, the worker processes and deletes it.
 * The payload may contain child data (REGISTER), so rows never live longer than the retry window.
 */
@Repository
public class AuthRequestRepository {

    public enum Kind {
        REGISTER,
        MAGIC_LINK
    }

    public record AuthRequest(
            UUID id,
            Kind kind,
            String email,
            @Nullable String payload,
            String bindingHash,
            int attempts,
            Instant createdAt) {}

    private final JdbcClient jdbc;

    public AuthRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Kind kind, String email, @Nullable String payloadJson, String bindingHash, Instant now) {
        jdbc.sql("""
                        INSERT INTO auth_request (kind, email, payload, binding_hash, next_attempt_at, created_at)
                        VALUES (:kind, :email, CAST(:payload AS jsonb), :bindingHash, :now, :now)
                        """)
                .param("kind", kind.name())
                .param("email", email)
                .param("payload", payloadJson)
                .param("bindingHash", bindingHash)
                .param("now", utc(now))
                .update();
    }

    /** Due rows, locked; other workers skip them (FOR UPDATE SKIP LOCKED). */
    public List<AuthRequest> lockDue(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT id, kind, email, payload::text AS payload, binding_hash, attempts, created_at
                        FROM auth_request
                        WHERE next_attempt_at <= :now
                        ORDER BY next_attempt_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("now", utc(now))
                .param("limit", limit)
                .query((rs, rowNum) -> new AuthRequest(
                        rs.getObject("id", UUID.class),
                        Kind.valueOf(rs.getString("kind")),
                        rs.getString("email"),
                        rs.getString("payload"),
                        rs.getString("binding_hash"),
                        rs.getInt("attempts"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .list();
    }

    public void delete(UUID id) {
        jdbc.sql("DELETE FROM auth_request WHERE id = :id").param("id", id).update();
    }

    public void reschedule(UUID id, int attempts, Instant nextAttemptAt) {
        jdbc.sql("UPDATE auth_request SET attempts = :attempts, next_attempt_at = :next WHERE id = :id")
                .param("id", id)
                .param("attempts", attempts)
                .param("next", utc(nextAttemptAt))
                .update();
    }
}
