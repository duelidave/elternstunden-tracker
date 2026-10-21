package de.elternstunden.audit;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Append-only audit log (SEC-11). {@code details} carries IDs and technical values only, never e-mails, names or
 * free text (SEC-12). Writes join the caller's transaction.
 */
@Service
public class AuditLog {

    public enum ActorKind {
        USER,
        SYSTEM,
        INTEGRATION
    }

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public AuditLog(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public void user(UUID actor, String action, String entityType, @Nullable Object entityId) {
        write(actor, ActorKind.USER, action, entityType, entityId, Map.of());
    }

    public void system(String action, String entityType, @Nullable Object entityId, Map<String, Object> details) {
        write(null, ActorKind.SYSTEM, action, entityType, entityId, details);
    }

    public void write(
            @Nullable UUID actor,
            ActorKind kind,
            String action,
            String entityType,
            @Nullable Object entityId,
            Map<String, Object> details) {
        jdbc.sql("""
                        INSERT INTO audit_log (occurred_at, actor_user_id, actor_kind, action, entity_type, entity_id, details)
                        VALUES (:now, :actor, :kind, :action, :entityType, :entityId, CAST(:details AS jsonb))
                        """)
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("actor", actor)
                .param("kind", kind.name())
                .param("action", action)
                .param("entityType", entityType)
                .param("entityId", entityId == null ? null : entityId.toString())
                .param("details", json.writeValueAsString(details))
                .update();
    }
}
