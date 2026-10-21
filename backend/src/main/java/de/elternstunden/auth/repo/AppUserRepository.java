package de.elternstunden.auth.repo;

import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.domain.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code app_user} access for the auth module. Times are always passed in (0.4 rule 7). */
@Repository
public class AppUserRepository {

    private static final String COLUMNS = "id, email, display_name, role, status, family_id, managed_by_config";

    private final JdbcClient jdbc;

    public AppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppUser> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id")
                .param("id", id)
                .query(AppUserRepository::map)
                .optional();
    }

    public Optional<AppUser> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(AppUserRepository::map)
                .optional();
    }

    public Optional<AppUser> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE email = :email")
                .param("email", email)
                .query(AppUserRepository::map)
                .optional();
    }

    /** Locks the account row, so parallel requests for the same address are serialised (7.2 token rules). */
    public Optional<AppUser> findByEmailForUpdate(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE email = :email FOR UPDATE")
                .param("email", email)
                .query(AppUserRepository::map)
                .optional();
    }

    public Optional<AppUser> findMaster() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE managed_by_config")
                .query(AppUserRepository::map)
                .optional();
    }

    /** Creates an unconfirmed account; empty if the address exists already (parallel registration, 7.1.4). */
    public Optional<UUID> insertPending(String email, String displayName, Instant now) {
        return jdbc.sql("""
                        INSERT INTO app_user (email, display_name, created_at, updated_at)
                        VALUES (:email, :displayName, :now, :now)
                        ON CONFLICT (email) DO NOTHING
                        RETURNING id
                        """)
                .param("email", email)
                .param("displayName", displayName)
                .param("now", utc(now))
                .query(UUID.class)
                .optional();
    }

    public void activate(UUID id, Instant now) {
        jdbc.sql("""
                        UPDATE app_user SET status = 'ACTIVE', email_verified_at = :now, updated_at = :now
                        WHERE id = :id AND status = 'PENDING_VERIFICATION'
                        """).param("id", id).param("now", utc(now)).update();
    }

    public void touchLogin(UUID id, Instant now) {
        jdbc.sql("UPDATE app_user SET last_login_at = :now WHERE id = :id")
                .param("id", id)
                .param("now", utc(now))
                .update();
    }

    /**
     * Deletes accounts that stayed unconfirmed past the cutoff and have no valid token left (7.1.4 step 9). The
     * status is checked again in the statement, so a parallel redemption that just activated the account wins.
     */
    public int deleteStalePending(Instant cutoff, Instant now) {
        return jdbc.sql("""
                        DELETE FROM app_user u
                        WHERE u.status = 'PENDING_VERIFICATION' AND u.created_at < :cutoff
                          AND NOT EXISTS (SELECT 1 FROM user_token t
                                          WHERE t.user_id = u.id AND t.used_at IS NULL AND t.expires_at > :now)
                        """).param("cutoff", utc(cutoff)).param("now", utc(now)).update();
    }

    /** Creates or updates the config-managed master account (7.9). */
    public UUID upsertMaster(String email, String displayName, String passwordHash, Instant now) {
        Optional<UUID> existing = jdbc.sql("SELECT id FROM app_user WHERE managed_by_config")
                .query(UUID.class)
                .optional();
        if (existing.isPresent()) {
            jdbc.sql("""
                            UPDATE app_user SET email = :email, display_name = :displayName,
                                   password_hash = :hash, updated_at = :now
                            WHERE id = :id
                            """)
                    .param("id", existing.get())
                    .param("email", email)
                    .param("displayName", displayName)
                    .param("hash", passwordHash)
                    .param("now", utc(now))
                    .update();
            return existing.get();
        }
        return jdbc.sql("""
                        INSERT INTO app_user (email, display_name, role, status, managed_by_config, password_hash,
                                              email_verified_at, created_at, updated_at)
                        VALUES (:email, :displayName, 'MASTER_ADMIN', 'ACTIVE', true, :hash, :now, :now, :now)
                        RETURNING id
                        """)
                .param("email", email)
                .param("displayName", displayName)
                .param("hash", passwordHash)
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    public Optional<String> passwordHash(UUID id) {
        return jdbc.sql("SELECT password_hash FROM app_user WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .optional();
    }

    static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static AppUser map(ResultSet rs, int rowNum) throws SQLException {
        return new AppUser(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                UserRole.valueOf(rs.getString("role")),
                UserStatus.valueOf(rs.getString("status")),
                nullableUuid(rs, "family_id"),
                rs.getBoolean("managed_by_config"));
    }

    private static @Nullable UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
