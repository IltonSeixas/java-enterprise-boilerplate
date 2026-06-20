package com.enterprise.boilerplate.infrastructure.audit;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres")
class AuditLogHealthIndicator implements HealthIndicator {

    private final AuditLogJpaRepository jpa;

    AuditLogHealthIndicator(AuditLogJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Health health() {
        try {
            jpa.count();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
