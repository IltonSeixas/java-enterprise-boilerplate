package com.enterprise.boilerplate.application.port.out;

import com.enterprise.boilerplate.domain.entity.User;

import java.util.Optional;

public interface TokenServicePort {

    String issueAccessToken(User user);

    String issueRefreshToken(User user);

    Optional<String> validateAccessToken(String token);

    Optional<String> resolveUserIdFromRefreshToken(String refreshToken);

    /**
     * Reports whether {@code refreshToken} was already rotated out (used and revoked)
     * within the reuse-detection tombstone window, and — if so — which user it belonged
     * to. A hit here means the token is being replayed after rotation: either the client
     * retried a stale token, or the token was stolen and the thief raced the legitimate
     * holder. Callers should treat a hit as a theft signal and revoke the whole token
     * family via {@link #revokeAllRefreshTokens(String)}.
     */
    Optional<String> checkReuse(String refreshToken);

    void revokeRefreshToken(String refreshToken);

    void revokeAllRefreshTokens(String userId);
}
