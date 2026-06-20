package com.enterprise.boilerplate.infrastructure.security;

import com.enterprise.boilerplate.application.port.out.TokenServicePort;
import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.enterprise.boilerplate.infrastructure.cache.RedisTokenStore;
import com.enterprise.boilerplate.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtTokenService implements TokenServicePort {

    private static final String ROLE_CLAIM = "role";
    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String USER_REFRESH_PREFIX = "user-refresh:";
    private static final String KEY_ALGORITHM = "Ed25519";

    private final Path privateKeyPath;
    private final Path publicKeyPath;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;
    private final RedisTokenStore tokenStore;

    private volatile PrivateKey signingKey;
    private volatile PublicKey verificationKey;

    public JwtTokenService(JwtProperties jwtProperties, RedisTokenStore tokenStore) {
        this.privateKeyPath = Path.of(jwtProperties.privateKeyPath());
        this.publicKeyPath = Path.of(jwtProperties.publicKeyPath());
        this.accessTokenTtlSeconds = jwtProperties.accessTokenExpiryMinutes() * 60;
        this.refreshTokenTtlSeconds = jwtProperties.refreshTokenExpiryDays() * 86400;
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
                .signWith(signingKey())
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
                    .verifyWith(verificationKey())
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
                    .verifyWith(verificationKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get(ROLE_CLAIM, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }

    private PrivateKey signingKey() {
        PrivateKey key = signingKey;
        if (key == null) {
            synchronized (this) {
                key = signingKey;
                if (key == null) {
                    key = signingKey = loadPrivateKey(privateKeyPath);
                }
            }
        }
        return key;
    }

    private PublicKey verificationKey() {
        PublicKey key = verificationKey;
        if (key == null) {
            synchronized (this) {
                key = verificationKey;
                if (key == null) {
                    key = verificationKey = loadPublicKey(publicKeyPath);
                }
            }
        }
        return key;
    }

    private static PrivateKey loadPrivateKey(Path path) {
        byte[] der = decodePem(readPem(path));
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to load JWT private key from " + path, e);
        }
    }

    private static PublicKey loadPublicKey(Path path) {
        byte[] der = decodePem(readPem(path));
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to load JWT public key from " + path, e);
        }
    }

    private static String readPem(Path path) {
        try {
            return Files.readString(path, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JWT key file " + path, e);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
