package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.AuthResponse;
import com.enterprise.boilerplate.application.dto.LoginRequest;
import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;

public class LoginUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenServicePort tokenService;
    private final AuditPort audit;
    private final long accessTokenExpiryMinutes;

    public LoginUserUseCase(UserRepository userRepository,
                            PasswordHasherPort passwordHasher,
                            TokenServicePort tokenService,
                            AuditPort audit,
                            long accessTokenExpiryMinutes) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.audit = audit;
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
    }

    public AuthResponse execute(LoginRequest request) {
        var email = Email.of(request.email());
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    audit.record(AuditEvent.of(AuditEventType.LOGIN_FAILED, email.value(), "user not found"));
                    return new UserNotFoundException(email.value());
                });

        if (!user.isActive()) {
            audit.record(AuditEvent.of(AuditEventType.LOGIN_FAILED, user.getId().toString(), "inactive user"));
            throw new InactiveUserException();
        }

        if (!passwordHasher.verify(request.password(), user.getPasswordHash())) {
            audit.record(AuditEvent.of(AuditEventType.LOGIN_FAILED, user.getId().toString(), "invalid password"));
            throw new InvalidPasswordException();
        }

        String accessToken = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueRefreshToken(user);

        audit.record(AuditEvent.of(AuditEventType.LOGIN_SUCCEEDED, user.getId().toString(), null));

        return AuthResponse.of(accessToken, refreshToken, accessTokenExpiryMinutes * 60);
    }
}
