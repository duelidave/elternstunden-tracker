package de.elternstunden.timeentry.repo;

import de.elternstunden.timeentry.domain.EntryContent;
import de.elternstunden.timeentry.domain.EntryStatus;
import de.elternstunden.timeentry.domain.EntryView;
import de.elternstunden.timeentry.domain.TimeEntry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@code time_entry}. Deleted entries (soft delete) are invisible to every read. Every change raises {@code version}
 * (8.1), also confirming and reopening. Times are always passed in (0.4 rule 7).
 */
@Repository
public class TimeEntryRepository {

    private static final String COLUMNS = "id, family_id, school_year_id, created_by, work_date, start_time,"
            + " duration_minutes, description, status, confirmed_by, confirmed_at, created_at, version";

    private static final String VIEW = """
            SELECT te.id, te.family_id, te.school_year_id, te.created_by, te.work_date, te.start_time,
                   te.duration_minutes, te.description, te.status, te.confirmed_by, te.confirmed_at, te.created_at,
                   te.version, creator.display_name AS created_by_name, confirmer.display_name AS confirmed_by_name,
                   sy.closed_at IS NOT NULL AS school_year_closed
            FROM time_entry te
            JOIN app_user creator ON creator.id = te.created_by
            LEFT JOIN app_user confirmer ON confirmer.id = te.confirmed_by
            JOIN school_year sy ON sy.id = te.school_year_id
            WHERE te.deleted_at IS NULL
            """;

    /** An entry with the version an admin saw before confirming it (7.5 point 3). */
    public record Seen(UUID id, int version) {}

    /** Confirmed and open minutes of a family in a school year. */
    public record Sums(int confirmedMinutes, int openMinutes) {}

    private final JdbcClient jdbc;

    public TimeEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TimeEntry> findByIdForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM time_entry WHERE id = :id AND deleted_at IS NULL FOR UPDATE")
                .param("id", id)
                .query(TimeEntryRepository::map)
                .optional();
    }

    public Optional<EntryView> findView(UUID id) {
        return jdbc.sql(VIEW + " AND te.id = :id")
                .param("id", id)
                .query(TimeEntryRepository::mapView)
                .optional();
    }

    /** Newest first: work date, then creation (7.3 rule 3). */
    public List<EntryView> findByFamilyAndYear(UUID familyId, UUID schoolYearId) {
        return jdbc.sql(VIEW + " AND te.family_id = :familyId AND te.school_year_id = :yearId"
                        + " ORDER BY te.work_date DESC, te.created_at DESC, te.id")
                .param("familyId", familyId)
                .param("yearId", schoolYearId)
                .query(TimeEntryRepository::mapView)
                .list();
    }

    public Sums sums(UUID familyId, UUID schoolYearId) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(duration_minutes) FILTER (WHERE status = 'CONFIRMED'), 0) AS confirmed,
                               COALESCE(SUM(duration_minutes) FILTER (WHERE status = 'OPEN'), 0) AS open
                        FROM time_entry
                        WHERE family_id = :familyId AND school_year_id = :yearId AND deleted_at IS NULL
                        """)
                .param("familyId", familyId)
                .param("yearId", schoolYearId)
                .query((rs, rowNum) -> new Sums(rs.getInt("confirmed"), rs.getInt("open")))
                .single();
    }

    /** Serializes entries of one family until the transaction ends (7.4 rule 5, AC-ENTRY-11). */
    public void lockFamily(UUID familyId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 1))")
                .param("key", "time_entry:" + familyId)
                .query((rs, rowNum) -> rs.getObject(1))
                .single();
    }

    /** The same content for the same family created since the given time (7.4 rule 5). */
    public boolean duplicateSince(UUID familyId, EntryContent content, Instant since) {
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM time_entry
                                       WHERE family_id = :familyId AND work_date = :workDate
                                         AND start_time IS NOT DISTINCT FROM CAST(:startTime AS time)
                                         AND duration_minutes = :duration AND description = :description
                                         AND deleted_at IS NULL AND created_at >= :since)
                        """)
                .param("familyId", familyId)
                .param("workDate", content.workDate())
                .param("startTime", content.startTime())
                .param("duration", content.durationMinutes())
                .param("description", content.description())
                .param("since", utc(since))
                .query(Boolean.class)
                .single();
    }

    public UUID insert(UUID familyId, UUID schoolYearId, UUID createdBy, EntryContent content, Instant now) {
        return jdbc.sql("""
                        INSERT INTO time_entry (family_id, school_year_id, created_by, work_date, start_time,
                                                duration_minutes, description, created_at, updated_at)
                        VALUES (:familyId, :yearId, :createdBy, :workDate, :startTime, :duration, :description,
                                :now, :now)
                        RETURNING id
                        """)
                .param("familyId", familyId)
                .param("yearId", schoolYearId)
                .param("createdBy", createdBy)
                .param("workDate", content.workDate())
                .param("startTime", content.startTime())
                .param("duration", content.durationMinutes())
                .param("description", content.description())
                .param("now", utc(now))
                .query(UUID.class)
                .single();
    }

    /** New content; with {@code reopen} a confirmed entry becomes OPEN again (INV-07). */
    public void updateContent(UUID id, UUID schoolYearId, EntryContent content, boolean reopen, Instant now) {
        jdbc.sql("""
                        UPDATE time_entry
                        SET school_year_id = :yearId, work_date = :workDate, start_time = :startTime,
                            duration_minutes = :duration, description = :description, updated_at = :now,
                            version = version + 1,
                            status = CASE WHEN :reopen THEN 'OPEN'::entry_status ELSE status END,
                            confirmed_by = CASE WHEN :reopen THEN NULL ELSE confirmed_by END,
                            confirmed_at = CASE WHEN :reopen THEN NULL ELSE confirmed_at END
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("yearId", schoolYearId)
                .param("workDate", content.workDate())
                .param("startTime", content.startTime())
                .param("duration", content.durationMinutes())
                .param("description", content.description())
                .param("reopen", reopen)
                .param("now", utc(now))
                .update();
    }

    public void softDelete(UUID id, Instant now) {
        jdbc.sql("UPDATE time_entry SET deleted_at = :now, updated_at = :now, version = version + 1 WHERE id = :id")
                .param("id", id)
                .param("now", utc(now))
                .update();
    }

    /**
     * Confirms open entries of open school years in one statement (7.5 point 4), only in the version the admin saw;
     * returns the confirmed ids. Already confirmed, changed entries and entries of closed years stay unchanged.
     */
    public List<UUID> confirm(List<Seen> seen, UUID adminId, Instant now) {
        return jdbc.sql("""
                        UPDATE time_entry te
                           SET status = 'CONFIRMED', confirmed_by = :adminId, confirmed_at = :now,
                               updated_at = :now, version = te.version + 1
                          FROM school_year sy, unnest(CAST(:ids AS uuid[]), CAST(:versions AS integer[])) AS seen (id, version)
                         WHERE te.id = seen.id AND te.version = seen.version
                           AND te.status = 'OPEN' AND te.deleted_at IS NULL
                           AND sy.id = te.school_year_id AND sy.closed_at IS NULL
                        RETURNING te.id
                        """)
                .param("ids", seen.stream().map(Seen::id).toArray(UUID[]::new))
                .param("versions", seen.stream().map(Seen::version).toArray(Integer[]::new))
                .param("adminId", adminId)
                .param("now", utc(now))
                .query(UUID.class)
                .list();
    }

    /** Admin list (7.5): optional filters, oldest creation first. */
    public List<EntryView> search(
            @Nullable EntryStatus status, @Nullable UUID familyId, @Nullable LocalDate from, @Nullable LocalDate to) {
        return jdbc.sql(VIEW
                        + " AND (CAST(:status AS entry_status) IS NULL OR te.status = CAST(:status AS entry_status))"
                        + " AND (CAST(:familyId AS uuid) IS NULL OR te.family_id = :familyId)"
                        + " AND (CAST(:from AS date) IS NULL OR te.work_date >= :from)"
                        + " AND (CAST(:to AS date) IS NULL OR te.work_date <= :to)"
                        + " ORDER BY te.created_at, te.id")
                .param("status", status == null ? null : status.name())
                .param("familyId", familyId)
                .param("from", from)
                .param("to", to)
                .query(TimeEntryRepository::mapView)
                .list();
    }

    public int countOpen() {
        return jdbc.sql("SELECT count(*) FROM time_entry WHERE status = 'OPEN' AND deleted_at IS NULL")
                .query(Integer.class)
                .single();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static EntryView mapView(ResultSet rs, int rowNum) throws SQLException {
        return new EntryView(
                map(rs, rowNum),
                rs.getString("created_by_name"),
                rs.getString("confirmed_by_name"),
                rs.getBoolean("school_year_closed"));
    }

    private static TimeEntry map(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime confirmedAt = rs.getObject("confirmed_at", OffsetDateTime.class);
        return new TimeEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getObject("school_year_id", UUID.class),
                rs.getObject("created_by", UUID.class),
                rs.getObject("work_date", LocalDate.class),
                rs.getObject("start_time", LocalTime.class),
                rs.getInt("duration_minutes"),
                rs.getString("description"),
                EntryStatus.valueOf(rs.getString("status")),
                rs.getObject("confirmed_by", UUID.class),
                confirmedAt == null ? null : confirmedAt.toInstant(),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getInt("version"));
    }
}
