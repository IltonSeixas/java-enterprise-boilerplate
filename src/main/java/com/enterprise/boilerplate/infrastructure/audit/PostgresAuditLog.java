package com.enterprise.boilerplate.infrastructure.audit;

import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("postgres")
class PostgresAuditLog implements AuditPort {

    private static final Logger log = LoggerFactory.getLogger(PostgresAuditLog.class);

    private final AuditLogJpaRepository jpa;

    PostgresAuditLog(AuditLogJpaRepository jpa) {
        this.jpa = jpa;
    }

    // Auditing must never fail the use case it observes — a Postgres outage here
    // degrades to a logged warning instead of blocking login/registration/etc.
    @Override
    public void record(AuditEvent event) {
        try {
            persist(event);
        } catch (Exception e) {
            log.warn("failed to persist audit event type={} actor={} target={}: {}",
                    event.type(), event.actorUserId(), event.targetUserId(), e.getMessage());
        }
    }

    @Transactional
    @CircuitBreaker(name = "postgres")
    void persist(AuditEvent event) {
        jpa.save(AuditLogJpaEntity.from(event));
    }
}
