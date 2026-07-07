package com.enterprise.boilerplate.infrastructure.security;

import com.enterprise.boilerplate.application.dto.RefreshTokenRedemption;
import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.enterprise.boilerplate.infrastructure.cache.RedisTokenStore;
import com.enterprise.boilerplate.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtTokenService implements TokenServicePort {

    private static final String ROLE_CLAIM = "role";
    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String USER_REFRESH_PREFIX = "user-refresh:";
    private static final String USED_REFRESH_PREFIX = "used-refresh:";

    // How long a rotated-out refresh token is remembered as "already used" so a
    // replay of it can be detected as a reuse/theft signal. Shorter than the
    // refresh token's own TTL — this only needs to outlast realistic client retry
    // windows (network hiccups, double-submits), not the full session lifetime.
    private static final long REUSE_TOMBSTONE_TTL_SECONDS = 300;

    private final Path privateKeyPath;
    private final Path publicKeyPath;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;
    private final RedisTokenStore tokenStore;

    private PrivateKey signingKey;
    private PublicKey verificationKey;

    public JwtTokenService(JwtProperties jwtProperties, RedisTokenStore tokenStore) {
        this.privateKeyPath = Path.of(jwtProperties.privateKeyPath());
        this.publicKeyPath = Path.of(jwtProperties.publicKeyPath());
        this.accessTokenTtlSeconds = jwtProperties.accessTokenExpiryMinutes() * 60;
        this.refreshTokenTtlSeconds = jwtProperties.refreshTokenExpiryDays() * 86400;
        this.tokenStore = tokenStore;
    }

    @PostConstruct
    void loadKeys() {
        this.signingKey = Ed25519PemKeyLoader.loadPrivateKey(privateKeyPath);
        this.verificationKey = Ed25519PemKeyLoader.loadPublicKey(publicKeyPath);
    }

    @Override
    public String issueAccessToken(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.id().toString())
                .claim(ROLE_CLAIM, user.role().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenTtlSeconds * 1000))
                .signWith(signingKey())
                .compact();
    }

    @Override
    public String issueRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        String userId = user.id().toString();

        tokenStore.set(REFRESH_KEY_PREFIX + token, userId, refreshTokenTtlSeconds);
        tokenStore.set(USER_REFRESH_PREFIX + userId + ":" + token, token, refreshTokenTtlSeconds);

        return token;
    }

    @Override
    public Optional<String> validateAccessToken(String token) {
        return parseAccessToken(token).map(TokenClaims::subject);
    }

    public Optional<TokenClaims> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(verificationKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenClaims(claims.getSubject(), claims.get(ROLE_CLAIM, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> resolveUserIdFromRefreshToken(String refreshToken) {
        return tokenStore.get(REFRESH_KEY_PREFIX + refreshToken);
    }

    @Override
    public RefreshTokenRedemption redeemRefreshToken(String refreshToken) {
        RedisTokenStore.RedeemResult result = tokenStore.redeemRefreshToken(
                USED_REFRESH_PREFIX + refreshToken, REFRESH_KEY_PREFIX + refreshToken, REUSE_TOMBSTONE_TTL_SECONDS);

        return switch (result) {
            case RedisTokenStore.RedeemResult.Reused reused -> new RefreshTokenRedemption.Reused(reused.userId());
            case RedisTokenStore.RedeemResult.Redeemed redeemed -> {
                // The atomic script already deleted refresh:<token> and wrote the
                // tombstone; the per-user index entry has no reuse-detection role, so
                // it is cleaned up here as a regular (non-atomic) follow-up delete.
                tokenStore.delete(USER_REFRESH_PREFIX + redeemed.userId() + ":" + refreshToken);
                yield new RefreshTokenRedemption.Redeemed(redeemed.userId());
            }
            case RedisTokenStore.RedeemResult.Invalid ignored -> new RefreshTokenRedemption.Invalid();
        };
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        tokenStore.get(REFRESH_KEY_PREFIX + refreshToken).ifPresent(userId -> {
            tokenStore.delete(USER_REFRESH_PREFIX + userId + ":" + refreshToken);
            tokenStore.set(USED_REFRESH_PREFIX + refreshToken, userId, REUSE_TOMBSTONE_TTL_SECONDS);
        });
        tokenStore.delete(REFRESH_KEY_PREFIX + refreshToken);
    }

    @Override
    public void revokeAllRefreshTokens(String userId) {
        String userKeyPrefix = USER_REFRESH_PREFIX + userId + ":";
        tokenStore.deleteByPattern(userKeyPrefix + "*", fullKey -> {
            String bareToken = fullKey.substring(userKeyPrefix.length());
            tokenStore.delete(REFRESH_KEY_PREFIX + bareToken);
            tokenStore.delete(fullKey);
        });
    }

    private PrivateKey signingKey() {
        return signingKey;
    }

    private PublicKey verificationKey() {
        return verificationKey;
    }
}
