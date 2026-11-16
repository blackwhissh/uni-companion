package com.unicompanion.learning.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit trail for privileged learning actions (visibility, ownership, etc.).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void record(
            UUID actorId,
            String action,
            String resourceType,
            String resourceId,
            Map<String, ?> details,
            String correlationId
    ) {
        Instant now = Instant.now();
        jdbc.update(
                """
                        insert into audit_events (id, actor_id, action, resource_type, resource_id, details, correlation_id, created_at)
                        values (?, ?, ?, ?, ?, cast(? as jsonb), ?, ?)
                        """,
                UUID.randomUUID(),
                actorId,
                action,
                resourceType,
                resourceId,
                toJson(details),
                correlationId,
                Timestamp.from(now)
        );
        log.info(
                "Audit action={} actor={} resource={}/{} correlationId={}",
                action,
                actorId,
                resourceType,
                resourceId,
                correlationId
        );
    }

    private static String toJson(Map<String, ?> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : details.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        return json.append('}').toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
