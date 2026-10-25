package de.elternstunden.user.repo;

import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.user.domain.UserAccount;
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

/** {@code app_user} for the user administration. Times are always passed in (0.4 rule 7). */
@Repository
public class UserAccountRepository {

    private static final String COLUMNS = "id, email, display_name, role, status, family_id, managed_by_config,"
            + " email_verified_at, last_login_at, created_at, anonymized_at, totp_enabled_at";

    private final JdbcClient jdbc;

    public UserAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first. */
    public List<UserAccount> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user ORDER BY created_at DESC, id")
                .query(UserAccountRepository::map)
                .list();
    }

    public Optional<UserAccount> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id")
                .param("id", id)
                .query(UserAccountRepository::map)
                .optional();
    }

    public Optional<UserAccount> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(UserAccountRepository::map)
                .optional();
    }

    public void setRole(UUID id, UserRole role, Instant now) {
        jdbc.sql("UPDATE app_user SET role = CAST(:role AS user_role), updated_at = :now WHERE id = :id")
                .param("id", id)
                .param("role", role.name())
                .param("now", utc(now))
                .update();
    }

    public void setStatus(UUID id, UserStatus status, Instant now) {
        jdbc.sql("UPDATE app_user SET status = CAST(:status AS user_status), updated_at = :now WHERE id = :id")
                .param("id", id)
                .param("status", status.name())
                .param("now", utc(now))
                .update();
    }

    /**
     * DP-05 (a): no personal data left on the account. Tokens and identities go away; the claims are deleted by the
     * caller. Entries keep pointing at the account.
     */
    public void anonymize(UUID id, Instant now) {
        jdbc.sql("""
                        UPDATE app_user
                        SET email = 'deleted-' || id || '@invalid', display_name = 'Gelöschter Nutzer',
                            status = 'DISABLED', family_id = NULL, claim_corrections = 0, last_login_at = NULL,
                            totp_secret_enc = NULL, totp_enabled_at = NULL,
                            anonymized_at = :now, updated_at = :now
                        WHERE id = :id
                        """).param("id", id).param("now", utc(now)).update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static UserAccount map(ResultSet rs, int rowNum) throws SQLException {
        Instant createdAt = instant(rs, "created_at");
        return new UserAccount(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                UserRole.valueOf(rs.getString("role")),
                UserStatus.valueOf(rs.getString("status")),
                rs.getObject("family_id", UUID.class),
                rs.getBoolean("managed_by_config"),
                rs.getObject("email_verified_at", OffsetDateTime.class) != null,
                instant(rs, "last_login_at"),
                createdAt == null ? Instant.EPOCH : createdAt,
                instant(rs, "anonymized_at"),
                rs.getObject("totp_enabled_at", OffsetDateTime.class) != null);
    }
}
