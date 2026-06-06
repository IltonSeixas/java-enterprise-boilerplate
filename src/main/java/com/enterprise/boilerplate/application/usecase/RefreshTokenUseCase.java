package com.enterprise.boilerplate.application.usecase;

import com.enterprise.boilerplate.application.dto.AuthResponse;
import com.enterprise.boilerplate.application.dto.RefreshTokenRequest;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final TokenServicePort tokenService;
    private final long accessTokenExpiryMinutes;

    public RefreshTokenUseCase(UserRepository userRepository,
                               TokenServicePort tokenService,
                               @Value("${jwt.access-token-expiry-minutes:15}") long accessTokenExpiryMinutes) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.accessTokenExpiryMinutes = accessTokenExpiryMinutes;
    }

    public AuthResponse execute(RefreshTokenRequest request) {
        String userId = tokenService.resolveUserIdFromRefreshToken(request.refreshToken())
                .orElseThrow(InvalidTokenException::new);

        var user = userRepository.findById(UserId.of(userId))
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isActive()) {
            tokenService.revokeRefreshToken(request.refreshToken());
            throw new InactiveUserException();
        }

        tokenService.revokeRefreshToken(request.refreshToken());

        String newAccessToken = tokenService.issueAccessToken(user);
        String newRefreshToken = tokenService.issueRefreshToken(user);

        return AuthResponse.of(newAccessToken, newRefreshToken, accessTokenExpiryMinutes * 60);
    }
}
