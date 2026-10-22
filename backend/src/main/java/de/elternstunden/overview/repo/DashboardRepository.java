package de.elternstunden.overview.repo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Queries for the admin dashboard widgets (7.6). */
@Repository
public class DashboardRepository {

    /** An automatic assignment from the audit log (7.1.6). */
    public record AutoAssignment(UUID userId, String displayName, UUID familyId, Instant assignedAt) {}

    private final JdbcClient jdbc;

    public DashboardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Newest first; accounts that were removed since are left out. */
    public List<AutoAssignment> autoAssignmentsSince(Instant since) {
        return jdbc.sql("""
                        SELECT u.id, u.display_name, CAST(a.details ->> 'familyId' AS uuid) AS family_id, a.occurred_at
                        FROM audit_log a JOIN app_user u ON u.id = CAST(a.entity_id AS uuid)
                        WHERE a.action = 'USER_ASSIGNED_TO_FAMILY' AND a.details ->> 'method' = 'AUTO_MATCH'
                          AND a.occurred_at >= :since
                        ORDER BY a.occurred_at DESC
                        """)
                .param("since", OffsetDateTime.ofInstant(since, ZoneOffset.UTC))
                .query((rs, rowNum) -> new AutoAssignment(
                        rs.getObject("id", UUID.class),
                        rs.getString("display_name"),
                        rs.getObject("family_id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant()))
                .list();
    }
}
