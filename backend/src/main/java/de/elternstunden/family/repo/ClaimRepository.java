package de.elternstunden.family.repo;

import de.elternstunden.family.domain.Claim;
import de.elternstunden.family.domain.MatchResult;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code registration_claim}. The open claim of a user is the one neither resolved nor superseded (at most one, see
 * index {@code registration_claim_open_idx}).
 */
@Repository
public class ClaimRepository {

    private static final String COLUMNS = "id, user_id, child_first_name, child_last_name, child_birth_date,"
            + " match_result, matched_child_id, binding_hash, created_at";

    private final JdbcClient jdbc;

    public ClaimRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Serializes all claims for the same child until the transaction ends, so parallel requests cannot exceed the
     * per-child limit (7.1.4 step 5, AC-REG-13).
     */
    public void lockClaimKey(String claimKey) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", claimKey)
                .query((rs, rowNum) -> rs.getObject(1))
                .single();
    }

    /** All claims for the child since the given time, including superseded ones (9.1). */
    public int countSince(String claimKey, Instant since) {
        return jdbc.sql("SELECT count(*) FROM registration_claim WHERE claim_key = :key AND created_at >= :since")
                .param("key", claimKey)
                .param("since", utc(since))
                .query(Integer.class)
                .single();
    }

    public Optional<Claim> findOpen(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM registration_claim"
                        + " WHERE user_id = :userId AND resolved_at IS NULL AND superseded_at IS NULL")
                .param("userId", userId)
                .query(ClaimRepository::map)
                .optional();
    }

    /** The open claim of the user is replaced or discarded (7.1.4 steps 4 and 7). */
    public void supersedeOpen(UUID userId, Instant now) {
        jdbc.sql("""
                        UPDATE registration_claim SET superseded_at = :now
                        WHERE user_id = :userId AND resolved_at IS NULL AND superseded_at IS NULL
                        """).param("userId", userId).param("now", utc(now)).update();
    }

    /** The open claim is done: the user was assigned (automatically or by an admin). */
    public void resolveOpen(UUID userId, Instant now) {
        jdbc.sql("""
                        UPDATE registration_claim SET resolved_at = :now
                        WHERE user_id = :userId AND resolved_at IS NULL AND superseded_at IS NULL
                        """).param("userId", userId).param("now", utc(now)).update();
    }

    public UUID insert(
            UUID userId,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String claimKey,
            MatchResult result,
            @Nullable UUID matchedChildId,
            @Nullable String bindingHash,
            Instant now) {
        return jdbc.sql("""
                        INSERT INTO registration_claim (user_id, child_first_name, child_last_name, child_birth_date,
                                                        claim_key, match_result, matched_child_id, binding_hash,
                                                        created_at)
                        VALUES (:userId, :first, :last, :birthDate, :key, :result, :matchedChildId, :bindingHash, :now)
                        RETURNING id
                        """)
                .param("userId", userId)
                .param("first", firstName)
                .param("last", lastName)
                .param("birthDate", birthDate)
                .param("key", claimKey)
                .param("result", result.name())
                .param("matchedChildId", matchedChildId)
                .param("bindingHash", bindingHash)
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    /** DP-05: done and superseded claims are deleted after the retention time; the open claim stays. */
    public int deleteClosedBefore(Instant cutoff) {
        return jdbc.sql("""
                        DELETE FROM registration_claim
                        WHERE resolved_at < :cutoff OR superseded_at < :cutoff
                        """).param("cutoff", utc(cutoff)).update();
    }

    public int deleteAllOf(UUID userId) {
        return jdbc.sql("DELETE FROM registration_claim WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Claim map(ResultSet rs, int rowNum) throws SQLException {
        return new Claim(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("child_first_name"),
                rs.getString("child_last_name"),
                rs.getObject("child_birth_date", LocalDate.class),
                MatchResult.valueOf(rs.getString("match_result")),
                rs.getObject("matched_child_id", UUID.class),
                rs.getString("binding_hash"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
