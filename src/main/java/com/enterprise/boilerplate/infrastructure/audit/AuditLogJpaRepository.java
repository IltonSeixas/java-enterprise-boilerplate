package com.enterprise.boilerplate.infrastructure.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, UUID> {
}
