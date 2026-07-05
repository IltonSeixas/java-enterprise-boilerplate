package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.ChangePasswordRequest;
import com.enterprise.boilerplate.application.port.out.AuditPort;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.audit.AuditEvent;
import com.enterprise.boilerplate.domain.audit.AuditEventType;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;

public class ChangePasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenServicePort tokenService;
    private final AuditPort audit;

    public ChangePasswordUseCase(UserRepository userRepository,
                                 PasswordHasherPort passwordHasher,
                                 TokenServicePort tokenService,
                                 AuditPort audit) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.audit = audit;
    }

    public void execute(String userId, ChangePasswordRequest request) {
        var id = UserId.of(userId);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordHasher.verify(request.currentPassword(), user.passwordHash())) {
            throw new InvalidPasswordException();
        }

        var newHash = passwordHasher.hash(request.newPassword());
        user.changePassword(newHash);
        userRepository.save(user);

        tokenService.revokeAllRefreshTokens(userId);

        audit.record(AuditEvent.of(AuditEventType.PASSWORD_CHANGED, userId, null));
    }
}
