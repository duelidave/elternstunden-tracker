package de.elternstunden.family.repo;

import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.family.domain.Family;
import de.elternstunden.family.domain.Member;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code family} and the accounts assigned to it. */
@Repository
public class FamilyRepository {

    private final JdbcClient jdbc;

    public FamilyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Family> findAll() {
        return jdbc.sql("SELECT id, label, note FROM family")
                .query(FamilyRepository::map)
                .list();
    }

    public Optional<Family> findById(UUID id) {
        return jdbc.sql("SELECT id, label, note FROM family WHERE id = :id")
                .param("id", id)
                .query(FamilyRepository::map)
                .optional();
    }

    public boolean exists(UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM family WHERE id = :id)")
                .param("id", id)
                .query(Boolean.class)
                .single();
    }

    public UUID insert(@Nullable String label, Instant now) {
        return jdbc.sql("INSERT INTO family (label, created_at, updated_at) VALUES (:label, :now, :now) RETURNING id")
                .param("label", label)
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    public void update(UUID id, @Nullable String label, @Nullable String note, Instant now) {
        jdbc.sql("UPDATE family SET label = :label, note = :note, updated_at = :now WHERE id = :id")
                .param("id", id)
                .param("label", label)
                .param("note", note)
                .param("now", utc(now))
                .update();
    }

    /**
     * Display name of every family: the label or, if empty, the distinct last names of the children (FS-ADMOV
     * "Bezeichnung oder abgeleitet aus Kindernachnamen"). Empty text if there is neither.
     */
    public Map<UUID, String> displayLabels() {
        Map<UUID, String> labels = new HashMap<>();
        jdbc.sql(DISPLAY_LABELS + " GROUP BY f.id").query(rs -> {
            labels.put(rs.getObject("id", UUID.class), displayLabel(rs));
        });
        return labels;
    }

    public String displayLabel(UUID id) {
        return jdbc.sql(DISPLAY_LABELS + " WHERE f.id = :id GROUP BY f.id")
                .param("id", id)
                .query((rs, rowNum) -> displayLabel(rs))
                .optional()
                .orElse("");
    }

    private static final String DISPLAY_LABELS = """
            SELECT f.id, COALESCE(NULLIF(btrim(f.label), ''),
                                  string_agg(DISTINCT c.last_name, ', ' ORDER BY c.last_name)) AS display_label
            FROM family f LEFT JOIN child c ON c.family_id = f.id
            """;

    private static String displayLabel(ResultSet rs) throws SQLException {
        String value = rs.getString("display_label");
        return value == null ? "" : value;
    }

    public List<Member> members(UUID familyId) {
        return jdbc.sql("""
                        SELECT id, display_name, email, status FROM app_user
                        WHERE family_id = :familyId ORDER BY display_name
                        """)
                .param("familyId", familyId)
                .query((rs, rowNum) -> new Member(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        UserStatus.valueOf(rs.getString("status"))))
                .list();
    }

    /** All assigned accounts, grouped by family. */
    public Map<UUID, List<Member>> membersByFamily() {
        Map<UUID, List<Member>> result = new HashMap<>();
        jdbc.sql("SELECT id, family_id, display_name, email, status FROM app_user WHERE family_id IS NOT NULL")
                .query(rs -> {
                    result.computeIfAbsent(rs.getObject("family_id", UUID.class), id -> new ArrayList<>())
                            .add(new Member(
                                    rs.getObject("id", UUID.class),
                                    rs.getString("display_name"),
                                    rs.getString("email"),
                                    UserStatus.valueOf(rs.getString("status"))));
                });
        return result;
    }

    /** Active members per family (partner hint F4, admin lists). */
    public Map<UUID, Integer> activeMemberCounts() {
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.sql("""
                        SELECT family_id, count(*) AS members FROM app_user
                        WHERE family_id IS NOT NULL AND status = 'ACTIVE' GROUP BY family_id
                        """).query(rs -> {
            counts.put(rs.getObject("family_id", UUID.class), rs.getInt("members"));
        });
        return counts;
    }

    /** Partner hint (7.3 rule 6): exactly one active member. */
    public int activeMemberCount(UUID familyId) {
        return jdbc.sql("SELECT count(*) FROM app_user WHERE family_id = :familyId AND status = 'ACTIVE'")
                .param("familyId", familyId)
                .query(Integer.class)
                .single();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Family map(ResultSet rs, int rowNum) throws SQLException {
        return new Family(rs.getObject("id", UUID.class), rs.getString("label"), rs.getString("note"));
    }
}
