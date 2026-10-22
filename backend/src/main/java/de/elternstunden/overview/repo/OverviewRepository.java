package de.elternstunden.overview.repo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The reference query of the admin overview (5.6, D-11). */
@Repository
public class OverviewRepository {

    /** Minutes of one family in the school year; only confirmed ones count against the target (D-10). */
    public record Minutes(int confirmedMinutes, int openMinutes, int missingMinutes) {}

    private final JdbcClient jdbc;

    public OverviewRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Families owing hours in the school year (a child is enrolled, OPEN-04) with their minutes. Enrollment is checked
     * with EXISTS, so siblings do not multiply the minutes (AC-OV-02).
     */
    public Map<UUID, Minutes> minutesByFamily(UUID schoolYearId) {
        Map<UUID, Minutes> result = new HashMap<>();
        jdbc.sql("""
                        SELECT f.id                              AS family_id,
                               COALESCE(te.confirmed_minutes, 0) AS confirmed_minutes,
                               COALESCE(te.open_minutes, 0)      AS open_minutes,
                               GREATEST(sy.required_minutes - COALESCE(te.confirmed_minutes, 0), 0) AS missing_minutes
                        FROM school_year sy
                        JOIN family f
                          ON EXISTS (SELECT 1
                                     FROM child c
                                     JOIN child_enrollment ce ON ce.child_id = c.id
                                     WHERE c.family_id = f.id
                                       AND ce.school_year_id = sy.id)
                        LEFT JOIN (
                            SELECT family_id,
                                   SUM(duration_minutes) FILTER (WHERE status = 'CONFIRMED') AS confirmed_minutes,
                                   SUM(duration_minutes) FILTER (WHERE status = 'OPEN')      AS open_minutes
                            FROM time_entry
                            WHERE school_year_id = :yearId
                              AND deleted_at IS NULL
                            GROUP BY family_id
                        ) te ON te.family_id = f.id
                        WHERE sy.id = :yearId
                        """).param("yearId", schoolYearId).query(rs -> {
            result.put(
                    rs.getObject("family_id", UUID.class),
                    new Minutes(
                            rs.getInt("confirmed_minutes"), rs.getInt("open_minutes"), rs.getInt("missing_minutes")));
        });
        return result;
    }
}
