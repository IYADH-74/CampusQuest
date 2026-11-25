package com.campusquest.audit;

import com.campusquest.common.Ids;
import com.campusquest.common.web.PageResponse;
import com.campusquest.common.web.RequestMetadata;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Append-only security audit trail. Writes run in their own transaction so that failed operations
 * (a rejected login, a detected token replay) are still recorded when the caller rolls back.
 */
@Service
public class AuditService {

    public record AuditEventView(UUID id, Instant occurredAt, UUID actorId, AuditAction action, String targetType,
            UUID targetId, String ipAddress, String requestId, JsonNode details) {}

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public AuditService(JdbcClient jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, UUID actorId, String targetType, UUID targetId, Map<String, ?> details) {
        RequestMetadata request = RequestMetadata.current();
        jdbc.sql("""
                insert into audit_events (id, occurred_at, actor_id, action, target_type, target_id, ip_address, request_id, details)
                values (:id, :occurredAt, :actorId, :action, :targetType, :targetId, :ip, :requestId, cast(:details as jsonb))
                """)
                .param("id", Ids.newId())
                .param("occurredAt", Timestamp.from(clock.instant()))
                .param("actorId", actorId)
                .param("action", action.name())
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("ip", request.ip())
                .param("requestId", RequestMetadata.currentRequestId())
                .param("details", json.writeValueAsString(details == null ? Map.of() : details))
                .update();
    }

    public void record(AuditAction action, UUID actorId, String targetType, UUID targetId) {
        record(action, actorId, targetType, targetId, Map.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventView> search(AuditAction action, UUID actorId, UUID targetId, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (action != null) {
            where.append(" and action = ?");
            params.add(action.name());
        }
        if (actorId != null) {
            where.append(" and actor_id = ?");
            params.add(actorId);
        }
        if (targetId != null) {
            where.append(" and target_id = ?");
            params.add(targetId);
        }
        long total = jdbc.sql("select count(*) from audit_events" + where).params(params).query(Long.class).single();
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((long) page * size);
        List<AuditEventView> rows = jdbc.sql("select id, occurred_at, actor_id, action, target_type, target_id, "
                        + "ip_address, request_id, details::text as details from audit_events" + where
                        + " order by occurred_at desc, id desc limit ? offset ?")
                .params(pageParams)
                .query((rs, n) -> new AuditEventView(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getObject("actor_id", UUID.class),
                        AuditAction.valueOf(rs.getString("action")),
                        rs.getString("target_type"),
                        rs.getObject("target_id", UUID.class),
                        rs.getString("ip_address"),
                        rs.getString("request_id"),
                        json.readTree(rs.getString("details"))))
                .list();
        return PageResponse.of(rows, page, size, total);
    }
}
