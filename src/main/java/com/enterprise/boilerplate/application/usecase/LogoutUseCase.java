package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;

public class LogoutUseCase {

    private final TokenServicePort tokenService;
    private final AuditPort audit;

    public LogoutUseCase(TokenServicePort tokenService, AuditPort audit) {
        this.tokenService = tokenService;
        this.audit = audit;
    }

    public void execute(String refreshToken) {
        String userId = tokenService.resolveUserIdFromRefreshToken(refreshToken)
                .orElseThrow(InvalidTokenException::new);
        tokenService.revokeRefreshToken(refreshToken);
        audit.record(AuditEvent.of(AuditEventType.LOGOUT, userId, null));
    }
}
