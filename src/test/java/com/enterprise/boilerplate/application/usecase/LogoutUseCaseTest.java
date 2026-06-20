package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock
    private TokenServicePort tokenService;

    @Mock
    private AuditPort audit;

    @Test
    void execute_revokesGivenRefreshTokenAndRecordsAuditEvent() {
        var useCase = new LogoutUseCase(tokenService, audit);
        when(tokenService.resolveUserIdFromRefreshToken("refresh-token")).thenReturn(Optional.of("user-id"));

        useCase.execute("refresh-token");

        verify(tokenService).revokeRefreshToken("refresh-token");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(AuditEventType.LOGOUT);
        assertThat(captor.getValue().actorUserId()).isEqualTo("user-id");
    }
}
