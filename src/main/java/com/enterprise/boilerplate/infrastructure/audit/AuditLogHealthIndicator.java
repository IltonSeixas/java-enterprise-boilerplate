package com.enterprise.boilerplate.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
class AuditLogHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AuditLogHealthIndicator.class);

    private final AuditLogJpaRepository jpa;

    AuditLogHealthIndicator(AuditLogJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Health health() {
        try {
            jpa.count();
            return Health.up().build();
        } catch (RuntimeException e) {
            log.error("Audit log health check failed", e);
            return Health.down().withDetail("error", "audit log unavailable").build();
        }
    }
}
