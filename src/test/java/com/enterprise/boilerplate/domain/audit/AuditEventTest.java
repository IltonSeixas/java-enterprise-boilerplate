package com.enterprise.boilerplate.domain.audit;

import com.enterprise.boilerplate.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventTest {

    @Test
    void of_withActorAndTarget_createsEventWithGeneratedIdAndTimestamp() {
        AuditEvent event = AuditEvent.of(AuditEventType.ROLE_CHANGED, "actor-1", "target-1", "USER -> ADMIN");

        assertThat(event.id()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.type()).isEqualTo(AuditEventType.ROLE_CHANGED);
        assertThat(event.actorUserId()).isEqualTo("actor-1");
        assertThat(event.targetUserId()).isEqualTo("target-1");
        assertThat(event.detail()).isEqualTo("USER -> ADMIN");
    }

    @Test
    void of_withoutExplicitTarget_setsTargetToNull() {
        AuditEvent event = AuditEvent.of(AuditEventType.LOGIN_SUCCEEDED, "actor-1", "detail");

        assertThat(event.targetUserId()).isNull();
    }

    @Test
    void of_withNullType_throwsDomainValidationException() {
        assertThatThrownBy(() -> AuditEvent.of(null, "actor-1", "detail"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void of_withBlankActor_throwsDomainValidationException() {
        assertThatThrownBy(() -> AuditEvent.of(AuditEventType.LOGOUT, "  ", "detail"))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void of_withNullDetail_isAllowed() {
        AuditEvent event = AuditEvent.of(AuditEventType.LOGOUT, "actor-1", null);

        assertThat(event.detail()).isNull();
    }
}
