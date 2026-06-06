package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.ChangePasswordRequest;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.springframework.stereotype.Service;

@Service
public class ChangePasswordUseCase {

    private final UserRepository userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenServicePort tokenService;

    public ChangePasswordUseCase(UserRepository userRepository,
                                 PasswordHasherPort passwordHasher,
                                 TokenServicePort tokenService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public void execute(String userId, ChangePasswordRequest request) {
        var id = UserId.of(userId);
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordHasher.verify(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException();
        }

        var newHash = passwordHasher.hash(request.newPassword());
        user.changePassword(newHash);
        userRepository.save(user);

        tokenService.revokeAllRefreshTokens(userId);
    }
}
