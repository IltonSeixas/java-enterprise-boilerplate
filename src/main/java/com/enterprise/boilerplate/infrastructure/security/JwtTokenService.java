package com.enterprise.boilerplate.infrastructure.security;

import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.enterprise.boilerplate.infrastructure.cache.RedisTokenStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtTokenService implements TokenServicePort {

    private static final String ROLE_CLAIM = "role";
    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String USER_REFRESH_PREFIX = "user-refresh:";

    private final SecretKey signingKey;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;
    private final RedisTokenStore tokenStore;

    public JwtTokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-minutes:15}") long accessTokenExpiryMinutes,
            @Value("${jwt.refresh-token-expiry-days:7}") long refreshTokenExpiryDays,
            RedisTokenStore tokenStore) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSeconds = accessTokenExpiryMinutes * 60;
        this.refreshTokenTtlSeconds = refreshTokenExpiryDays * 86400;
        this.tokenStore = tokenStore;
    }

    @Override
    public String issueAccessToken(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenTtlSeconds * 1000))
                .signWith(signingKey)
                .compact();
    }

    @Override
    public String issueRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        String userId = user.getId().toString();

        tokenStore.set(REFRESH_KEY_PREFIX + token, userId, refreshTokenTtlSeconds);
        tokenStore.set(USER_REFRESH_PREFIX + userId + ":" + token, token, refreshTokenTtlSeconds);

        return token;
    }

    @Override
    public Optional<String> validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> resolveUserIdFromRefreshToken(String refreshToken) {
        return tokenStore.get(REFRESH_KEY_PREFIX + refreshToken);
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        tokenStore.get(REFRESH_KEY_PREFIX + refreshToken).ifPresent(userId -> {
            tokenStore.delete(USER_REFRESH_PREFIX + userId + ":" + refreshToken);
        });
        tokenStore.delete(REFRESH_KEY_PREFIX + refreshToken);
    }

    @Override
    public void revokeAllRefreshTokens(String userId) {
        tokenStore.deleteByPattern(USER_REFRESH_PREFIX + userId + ":*", key -> {
            tokenStore.get(REFRESH_KEY_PREFIX + key).ifPresent(ignored ->
                tokenStore.delete(REFRESH_KEY_PREFIX + key)
            );
            tokenStore.delete(key);
        });
    }

    public String extractRole(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get(ROLE_CLAIM, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }
}
