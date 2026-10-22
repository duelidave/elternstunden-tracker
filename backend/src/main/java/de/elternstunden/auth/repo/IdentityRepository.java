package de.elternstunden.auth.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code user_identity}: accounts at OIDC providers, keyed by (issuer, subject), never by e-mail (D-23). */
@Repository
public class IdentityRepository {

    public record Identity(
            UUID id,
            UUID userId,
            String provider,
            @Nullable String emailAtLink,
            Instant createdAt,
            @Nullable Instant lastLoginAt) {}

    private static final String COLUMNS = "id, user_id, provider, email_at_link, created_at, last_login_at";

    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Identity> findByIssuerAndSubject(String issuer, String subject) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM user_identity WHERE issuer = :issuer AND subject = :subject")
                .param("issuer", issuer)
                .param("subject", subject)
                .query(IdentityRepository::map)
                .optional();
    }

    public List<Identity> findByUser(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM user_identity WHERE user_id = :userId ORDER BY created_at")
                .param("userId", userId)
                .query(IdentityRepository::map)
                .list();
    }

    public UUID insert(
            UUID userId, String provider, String issuer, String subject, @Nullable String email, Instant now) {
        return jdbc.sql("""
                        INSERT INTO user_identity (user_id, provider, issuer, subject, email_at_link, created_at,
                                                   last_login_at)
                        VALUES (:userId, :provider, :issuer, :subject, :email, :now, :now)
                        RETURNING id
                        """)
                .param("userId", userId)
                .param("provider", provider)
                .param("issuer", issuer)
                .param("subject", subject)
                .param("email", email)
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    public void touchLogin(UUID id, Instant now) {
        jdbc.sql("UPDATE user_identity SET last_login_at = :now WHERE id = :id")
                .param("id", id)
                .param("now", utc(now))
                .update();
    }

    /** Only the user's own identity (SEC-08); false if there was none. */
    public boolean delete(UUID id, UUID userId) {
        return jdbc.sql("DELETE FROM user_identity WHERE id = :id AND user_id = :userId")
                        .param("id", id)
                        .param("userId", userId)
                        .update()
                > 0;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Identity map(ResultSet rs, int rowNum) throws SQLException {
        return new Identity(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("provider"),
                rs.getString("email_at_link"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                instant(rs, "last_login_at"));
    }
}
