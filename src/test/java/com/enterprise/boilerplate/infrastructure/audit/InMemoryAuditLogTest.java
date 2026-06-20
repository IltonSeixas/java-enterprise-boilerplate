package com.enterprise.boilerplate.infrastructure.audit;

import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAuditLogTest {

    private InMemoryAuditLog auditLog;

    @BeforeEach
    void setUp() {
        auditLog = new InMemoryAuditLog();
    }

    @Test
    void record_appendsEventToLog() {
        AuditEvent event = AuditEvent.of(AuditEventType.LOGIN_SUCCEEDED, "user-1", null);

        auditLog.record(event);

        assertThat(auditLog.findAll()).containsExactly(event);
    }

    @Test
    void record_multipleEvents_preservesInsertionOrder() {
        AuditEvent first = AuditEvent.of(AuditEventType.USER_REGISTERED, "user-1", null);
        AuditEvent second = AuditEvent.of(AuditEventType.LOGIN_SUCCEEDED, "user-1", null);

        auditLog.record(first);
        auditLog.record(second);

        assertThat(auditLog.findAll()).containsExactly(first, second);
    }

    @Test
    void findAll_returnsImmutableSnapshot() {
        auditLog.record(AuditEvent.of(AuditEventType.LOGOUT, "user-1", null));

        var snapshot = auditLog.findAll();

        assertThat(snapshot).isUnmodifiable();
    }
}
