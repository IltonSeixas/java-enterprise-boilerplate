package com.enterprise.boilerplate.infrastructure.audit;

import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostgresAuditLogTest {

    @Mock private AuditLogJpaRepository jpa;

    @InjectMocks private PostgresAuditLog auditLog;

    private static AuditEvent loginEvent() {
        return new AuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                AuditEventType.LOGIN_SUCCEEDED,
                "actor-id",
                null,
                null);
    }

    @Test
    void record_savesEventToJpa() {
        AuditEvent event = loginEvent();

        auditLog.record(event);

        verify(jpa).save(any(AuditLogJpaEntity.class));
    }

    @Test
    void record_doesNotThrow_whenJpaSaveFails() {
        doThrow(new RuntimeException("DB down")).when(jpa).save(any());

        // Must degrade gracefully — an audit failure must not propagate to the caller.
        auditLog.record(loginEvent());
    }
}
