package com.enterprise.boilerplate.infrastructure.audit;

import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
class AuditLogJpaEntity {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private AuditEventType type;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private String actorUserId;

    @Column(name = "target_user_id", nullable = true, updatable = false)
    private String targetUserId;

    @Column(updatable = false)
    private String detail;

    protected AuditLogJpaEntity() {}

    static AuditLogJpaEntity from(AuditEvent event) {
        var entity = new AuditLogJpaEntity();
        entity.id = event.id();
        entity.occurredAt = event.occurredAt();
        entity.type = event.type();
        entity.actorUserId = event.actorUserId();
        entity.targetUserId = event.targetUserId();
        entity.detail = event.detail();
        return entity;
    }
}
