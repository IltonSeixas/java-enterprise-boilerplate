package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.AuthResponse;
import com.enterprise.boilerplate.application.dto.LoginRequest;
import com.enterprise.boilerplate.application.port.out.PasswordHasherPort;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoginUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasherPort passwordHasher;
    private final TokenServicePort tokenService;
    private final long accessTokenExpiryMinutes;

    public LoginUserUseCase(UserRepository userRepository,
                            PasswordHasherPort passwordHasher,
                            TokenServicePort tokenService,
                            @Value("${jwt.access-token-expiry-minutes:15}") long accessTokenExpiryMinutes) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
    }

    public AuthResponse execute(LoginRequest request) {
        var email = Email.of(request.email());
        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email.value()));

        if (!user.isActive()) {
            throw new InactiveUserException();
        }

        if (!passwordHasher.verify(request.password(), user.getPasswordHash())) {
            throw new InvalidPasswordException();
        }

        String accessToken = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueRefreshToken(user);

        return AuthResponse.of(accessToken, refreshToken, accessTokenExpiryMinutes * 60);
    }
}
