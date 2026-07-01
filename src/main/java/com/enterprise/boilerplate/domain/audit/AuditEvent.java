package com.enterprise.boilerplate.domain.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(
        UUID id,
        Instant occurredAt,
        AuditEventType type,
        String actorUserId,
        String targetUserId,
        String detail) {

    public AuditEvent {
        if (type == null) {
            throw new IllegalArgumentException("AuditEvent type must not be null");
        }
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new IllegalArgumentException("AuditEvent actorUserId must not be blank");
        }
    }

    public static AuditEvent of(AuditEventType type, String actorUserId, String targetUserId, String detail) {
        return new AuditEvent(UUID.randomUUID(), Instant.now(), type, actorUserId, targetUserId, detail);
    }

    public static AuditEvent of(AuditEventType type, String actorUserId, String detail) {
        return of(type, actorUserId, null, detail);
    }
}
