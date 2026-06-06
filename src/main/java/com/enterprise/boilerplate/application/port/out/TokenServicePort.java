package com.enterprise.boilerplate.application.port.out;

import com.enterprise.boilerplate.domain.entity.User;

import java.util.Optional;

public interface TokenServicePort {

    String issueAccessToken(User user);

    String issueRefreshToken(User user);

    Optional<String> validateAccessToken(String token);

    Optional<String> resolveUserIdFromRefreshToken(String refreshToken);

    void revokeRefreshToken(String refreshToken);

    void revokeAllRefreshTokens(String userId);
}
