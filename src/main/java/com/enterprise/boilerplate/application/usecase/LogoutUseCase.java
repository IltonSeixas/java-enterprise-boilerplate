package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;

public class LogoutUseCase {

    private final TokenServicePort tokenService;
    private final AuditPort audit;

    public LogoutUseCase(TokenServicePort tokenService, AuditPort audit) {
        this.tokenService = tokenService;
        this.audit = audit;
    }

    public void execute(String refreshToken) {
        String userId = tokenService.resolveUserIdFromRefreshToken(refreshToken).orElse("unknown");
        tokenService.revokeRefreshToken(refreshToken);
        audit.record(AuditEvent.of(AuditEventType.LOGOUT, userId, null));
    }
}
