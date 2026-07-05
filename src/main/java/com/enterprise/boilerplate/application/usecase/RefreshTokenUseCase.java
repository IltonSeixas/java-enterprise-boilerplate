package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.AuthResponse;
import com.enterprise.boilerplate.application.dto.RefreshTokenRequest;
import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;

public class RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final TokenServicePort tokenService;
    private final AuditPort audit;
    private final long accessTokenExpiryMinutes;

    public RefreshTokenUseCase(UserRepository userRepository,
                               TokenServicePort tokenService,
                               AuditPort audit,
                               long accessTokenExpiryMinutes) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.audit = audit;
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
    }

    public AuthResponse execute(RefreshTokenRequest request) {
        String userId = tokenService.resolveUserIdFromRefreshToken(request.refreshToken())
                .orElseThrow(InvalidTokenException::new);

        var user = userRepository.findById(UserId.of(userId))
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.active()) {
            tokenService.revokeRefreshToken(request.refreshToken());
            throw new InactiveUserException();
        }

        tokenService.revokeRefreshToken(request.refreshToken());

        String newAccessToken = tokenService.issueAccessToken(user);
        String newRefreshToken = tokenService.issueRefreshToken(user);

        audit.record(AuditEvent.of(AuditEventType.TOKEN_REFRESHED, userId, null));

        return AuthResponse.of(newAccessToken, newRefreshToken, accessTokenExpiryMinutes * 60);
    }
}
