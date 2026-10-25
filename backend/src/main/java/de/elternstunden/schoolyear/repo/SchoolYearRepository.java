package de.elternstunden.schoolyear.repo;

import de.elternstunden.schoolyear.domain.SchoolYear;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@code school_year} and enrollment bulk operations. Times are always passed in (0.4 rule 7). */
@Repository
public class SchoolYearRepository {

    private static final String COLUMNS = "id, label, starts_on, ends_on, required_minutes, closed_at";

    private final JdbcClient jdbc;

    public SchoolYearRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first. */
    public List<SchoolYear> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM school_year ORDER BY starts_on DESC")
                .query(SchoolYearRepository::map)
                .list();
    }

    public Optional<SchoolYear> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM school_year WHERE id = :id")
                .param("id", id)
                .query(SchoolYearRepository::map)
                .optional();
    }

    /** Entries lock their school year shared, so closing or re-dating it waits for them (INV-04, INV-09). */
    public Optional<SchoolYear> findByIdForShare(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM school_year WHERE id = :id FOR SHARE")
                .param("id", id)
                .query(SchoolYearRepository::map)
                .optional();
    }

    public Optional<SchoolYear> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM school_year WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(SchoolYearRepository::map)
                .optional();
    }

    /** The school year that ended last before this one started. */
    public Optional<SchoolYear> findPrevious(LocalDate startsOn) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM school_year WHERE ends_on < :startsOn ORDER BY ends_on DESC LIMIT 1")
                .param("startsOn", startsOn)
                .query(SchoolYearRepository::map)
                .optional();
    }

    public boolean overlapsOther(LocalDate startsOn, LocalDate endsOn, @Nullable UUID exceptId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM school_year
                                       WHERE daterange(starts_on, ends_on, '[]') && daterange(:startsOn, :endsOn, '[]')
                                         AND (CAST(:exceptId AS uuid) IS NULL OR id <> :exceptId))
                        """)
                .param("startsOn", startsOn)
                .param("endsOn", endsOn)
                .param("exceptId", exceptId)
                .query(Boolean.class)
                .single();
    }

    public boolean labelTaken(String label, @Nullable UUID exceptId) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM school_year
                                       WHERE label = :label AND (CAST(:exceptId AS uuid) IS NULL OR id <> :exceptId))
                        """)
                .param("label", label)
                .param("exceptId", exceptId)
                .query(Boolean.class)
                .single();
    }

    /** Entries of this year that would fall outside the given range (INV-09). */
    public boolean hasEntriesOutside(UUID id, LocalDate startsOn, LocalDate endsOn) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM time_entry
                                       WHERE school_year_id = :id AND deleted_at IS NULL
                                         AND (work_date < :startsOn OR work_date > :endsOn))
                        """)
                .param("id", id)
                .param("startsOn", startsOn)
                .param("endsOn", endsOn)
                .query(Boolean.class)
                .single();
    }

    public UUID insert(String label, LocalDate startsOn, LocalDate endsOn, int requiredMinutes, Instant now) {
        return jdbc.sql("""
                        INSERT INTO school_year (label, starts_on, ends_on, required_minutes, created_at)
                        VALUES (:label, :startsOn, :endsOn, :requiredMinutes, :now)
                        RETURNING id
                        """)
                .param("label", label)
                .param("startsOn", startsOn)
                .param("endsOn", endsOn)
                .param("requiredMinutes", requiredMinutes)
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    /**
     * Inserts unless the label exists or the range overlaps another year (both are constraints, so the check is
     * race-free). Empty if nothing was inserted.
     */
    public Optional<UUID> insertIfFree(String label, LocalDate startsOn, LocalDate endsOn, Instant now) {
        return jdbc.sql("""
                        INSERT INTO school_year (label, starts_on, ends_on, created_at)
                        VALUES (:label, :startsOn, :endsOn, :now)
                        ON CONFLICT DO NOTHING
                        RETURNING id
                        """)
                .param("label", label)
                .param("startsOn", startsOn)
                .param("endsOn", endsOn)
                .param("now", utc(now))
                .query(UUID.class)
                .optional();
    }

    public void update(UUID id, String label, LocalDate startsOn, LocalDate endsOn, int requiredMinutes) {
        jdbc.sql("""
                        UPDATE school_year
                        SET label = :label, starts_on = :startsOn, ends_on = :endsOn, required_minutes = :requiredMinutes
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("label", label)
                .param("startsOn", startsOn)
                .param("endsOn", endsOn)
                .param("requiredMinutes", requiredMinutes)
                .update();
    }

    public void setClosedAt(UUID id, @Nullable Instant closedAt) {
        jdbc.sql("UPDATE school_year SET closed_at = :closedAt WHERE id = :id")
                .param("id", id)
                .param("closedAt", closedAt == null ? null : utc(closedAt))
                .update();
    }

    /** Enrolls every child of the source year in the target year; returns the number of new enrollments. */
    public int copyEnrollments(UUID fromYearId, UUID toYearId) {
        return jdbc.sql("""
                        INSERT INTO child_enrollment (child_id, school_year_id)
                        SELECT child_id, :toYearId FROM child_enrollment WHERE school_year_id = :fromYearId
                        ON CONFLICT DO NOTHING
                        """)
                .param("fromYearId", fromYearId)
                .param("toYearId", toYearId)
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static SchoolYear map(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime closedAt = rs.getObject("closed_at", OffsetDateTime.class);
        return new SchoolYear(
                rs.getObject("id", UUID.class),
                rs.getString("label"),
                rs.getObject("starts_on", LocalDate.class),
                rs.getObject("ends_on", LocalDate.class),
                rs.getInt("required_minutes"),
                closedAt == null ? null : closedAt.toInstant());
    }
}
