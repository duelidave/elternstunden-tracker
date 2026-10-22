package de.elternstunden.family.repo;

import de.elternstunden.common.text.NameNormalizer;
import de.elternstunden.family.domain.Child;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
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

/**
 * {@code child} and {@code child_enrollment}. The normalized name columns are always derived here from the names
 * (7.1.3), so they can never get out of step.
 */
@Repository
public class ChildRepository {

    private static final String COLUMNS = "id, family_id, first_name, last_name, birth_date, external_ref";

    /** A child that is a candidate for an unassigned user, with the Levenshtein distance of the last names. */
    public record Candidate(Child child, boolean sameBirthDate, int distance) {}

    private final JdbcClient jdbc;

    public ChildRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Sorted by last name, first name. */
    public List<Child> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM child ORDER BY last_name_norm, first_name_norm, birth_date")
                .query(ChildRepository::map)
                .list();
    }

    public List<Child> findByFamily(UUID familyId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM child WHERE family_id = :familyId"
                        + " ORDER BY last_name_norm, first_name_norm, birth_date")
                .param("familyId", familyId)
                .query(ChildRepository::map)
                .list();
    }

    public Optional<Child> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM child WHERE id = :id")
                .param("id", id)
                .query(ChildRepository::map)
                .optional();
    }

    public Optional<Child> findByExternalRef(String externalRef) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM child WHERE external_ref = :ref")
                .param("ref", externalRef)
                .query(ChildRepository::map)
                .optional();
    }

    /** Exact match on normalized first name, last name and birth date (7.1.3). */
    public List<Child> findByIdentity(String firstName, String lastName, LocalDate birthDate) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM child"
                        + " WHERE last_name_norm = :last AND first_name_norm = :first AND birth_date = :birthDate")
                .param("first", NameNormalizer.normalize(firstName))
                .param("last", NameNormalizer.normalize(lastName))
                .param("birthDate", birthDate)
                .query(ChildRepository::map)
                .list();
    }

    /** Children with the same birth date or a similar last name (Levenshtein at most 2, 7.1.7), best first. */
    public List<Candidate> findCandidates(String lastName, LocalDate birthDate, int limit) {
        return jdbc.sql("""
                        SELECT %s, birth_date = :birthDate AS same_birth_date,
                               levenshtein(last_name_norm, :last) AS distance
                        FROM child
                        WHERE birth_date = :birthDate OR levenshtein(last_name_norm, :last) <= 2
                        ORDER BY same_birth_date DESC, distance, last_name_norm, first_name_norm
                        LIMIT :limit
                        """.formatted(COLUMNS))
                .param("birthDate", birthDate)
                .param("last", NameNormalizer.normalize(lastName))
                .param("limit", limit)
                .query((rs, rowNum) ->
                        new Candidate(map(rs, rowNum), rs.getBoolean("same_birth_date"), rs.getInt("distance")))
                .list();
    }

    public boolean externalRefTaken(String externalRef, @Nullable UUID exceptId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM child
                                       WHERE external_ref = :ref AND (CAST(:exceptId AS uuid) IS NULL OR id <> :exceptId))
                        """)
                .param("ref", externalRef)
                .param("exceptId", exceptId)
                .query(Boolean.class)
                .single();
    }

    public UUID insert(
            UUID familyId,
            String firstName,
            String lastName,
            LocalDate birthDate,
            @Nullable String externalRef,
            Instant now) {
        return jdbc.sql("""
                        INSERT INTO child (family_id, first_name, last_name, birth_date, first_name_norm, last_name_norm,
                                           external_ref, created_at, updated_at)
                        VALUES (:familyId, :first, :last, :birthDate, :firstNorm, :lastNorm, :ref, :now, :now)
                        RETURNING id
                        """)
                .param("familyId", familyId)
                .param("first", firstName)
                .param("last", lastName)
                .param("birthDate", birthDate)
                .param("firstNorm", NameNormalizer.normalize(firstName))
                .param("lastNorm", NameNormalizer.normalize(lastName))
                .param("ref", externalRef)
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    public void update(Child child, Instant now) {
        jdbc.sql("""
                        UPDATE child SET family_id = :familyId, first_name = :first, last_name = :last,
                               birth_date = :birthDate, first_name_norm = :firstNorm, last_name_norm = :lastNorm,
                               external_ref = :ref, updated_at = :now
                        WHERE id = :id
                        """)
                .param("id", child.id())
                .param("familyId", child.familyId())
                .param("first", child.firstName())
                .param("last", child.lastName())
                .param("birthDate", child.birthDate())
                .param("firstNorm", NameNormalizer.normalize(child.firstName()))
                .param("lastNorm", NameNormalizer.normalize(child.lastName()))
                .param("ref", child.externalRef())
                .param("now", utc(now))
                .update();
    }

    /** All enrollments, as school year ids per child. */
    public Map<UUID, List<UUID>> enrollments() {
        Map<UUID, List<UUID>> result = new HashMap<>();
        jdbc.sql("SELECT child_id, school_year_id FROM child_enrollment").query(rs -> {
            result.computeIfAbsent(rs.getObject("child_id", UUID.class), id -> new ArrayList<>())
                    .add(rs.getObject("school_year_id", UUID.class));
        });
        return result;
    }

    public boolean anyEnrolled(UUID familyId, UUID schoolYearId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM child c JOIN child_enrollment e ON e.child_id = c.id
                                       WHERE c.family_id = :familyId AND e.school_year_id = :yearId)
                        """)
                .param("familyId", familyId)
                .param("yearId", schoolYearId)
                .query(Boolean.class)
                .single();
    }

    public List<UUID> enrollmentsOf(UUID childId) {
        return jdbc.sql("SELECT school_year_id FROM child_enrollment WHERE child_id = :childId")
                .param("childId", childId)
                .query(UUID.class)
                .list();
    }

    /** True if the enrollment was new. */
    public boolean enroll(UUID childId, UUID schoolYearId) {
        return jdbc.sql("""
                        INSERT INTO child_enrollment (child_id, school_year_id) VALUES (:childId, :schoolYearId)
                        ON CONFLICT DO NOTHING
                        """)
                        .param("childId", childId)
                        .param("schoolYearId", schoolYearId)
                        .update()
                > 0;
    }

    /** True if an enrollment was removed. */
    public boolean unenroll(UUID childId, UUID schoolYearId) {
        return jdbc.sql("DELETE FROM child_enrollment WHERE child_id = :childId AND school_year_id = :schoolYearId")
                        .param("childId", childId)
                        .param("schoolYearId", schoolYearId)
                        .update()
                > 0;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Child map(ResultSet rs, int rowNum) throws SQLException {
        return new Child(
                rs.getObject("id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getObject("birth_date", LocalDate.class),
                rs.getString("external_ref"));
    }
}
