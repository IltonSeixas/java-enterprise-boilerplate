package com.enterprise.boilerplate.infrastructure.security;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.infrastructure.cache.RedisTokenStore;
import com.enterprise.boilerplate.config.properties.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final long ACCESS_TOKEN_EXPIRY_MINUTES = 15L;
    private static final long REFRESH_TOKEN_EXPIRY_DAYS = 7L;

    @Mock
    private RedisTokenStore tokenStore;

    @TempDir
    private Path tempDir;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create(
                Email.of("user@example.com"),
                PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"),
                "Alice",
                User.Role.ADMIN);
    }

    @Test
    void issueAccessToken_thenValidate_succeedsWithEdDSA() throws Exception {
        JwtTokenService service = newService(generateKeyPair());

        String token = service.issueAccessToken(user);

        assertThat(service.validateAccessToken(token))
                .contains(user.id().toString());
    }

    @Test
    void validateAccessToken_withTokenSignedByDifferentKeyPair_returnsEmpty() throws Exception {
        JwtTokenService signingService = newService(generateKeyPair());
        JwtTokenService verifyingService = newService(generateKeyPair());

        String token = signingService.issueAccessToken(user);

        assertThat(verifyingService.validateAccessToken(token)).isEmpty();
    }

    @Test
    void parseAccessToken_returnsRoleClaimFromIssuedToken() throws Exception {
        JwtTokenService service = newService(generateKeyPair());

        String token = service.issueAccessToken(user);

        assertThat(service.parseAccessToken(token))
                .hasValueSatisfying(claims -> assertThat(claims.role()).isEqualTo("ADMIN"));
    }

    @Test
    void checkReuse_returnsEmpty_whenTokenWasNeverRevoked() throws Exception {
        JwtTokenService service = newService(generateKeyPair());
        when(tokenStore.get("used-refresh:some-token")).thenReturn(Optional.empty());

        assertThat(service.checkReuse("some-token")).isEmpty();
    }

    @Test
    void revokeRefreshToken_writesTombstone_whenTokenExisted() throws Exception {
        JwtTokenService service = newService(generateKeyPair());
        String userId = user.id().toString();
        when(tokenStore.get("refresh:some-token")).thenReturn(Optional.of(userId));

        service.revokeRefreshToken("some-token");

        verify(tokenStore).set(eq("used-refresh:some-token"), eq(userId), anyLong());
        verify(tokenStore).delete("refresh:some-token");
    }

    @Test
    void revokeRefreshToken_doesNotWriteTombstone_whenTokenDidNotExist() throws Exception {
        JwtTokenService service = newService(generateKeyPair());
        when(tokenStore.get("refresh:unknown-token")).thenReturn(Optional.empty());

        service.revokeRefreshToken("unknown-token");

        verify(tokenStore, never()).set(anyString(), anyString(), anyLong());
        verify(tokenStore).delete("refresh:unknown-token");
    }

    @Test
    void checkReuse_returnsUserId_afterTokenWasRevoked() throws Exception {
        JwtTokenService service = newService(generateKeyPair());
        String userId = user.id().toString();
        when(tokenStore.get("used-refresh:replayed-token")).thenReturn(Optional.of(userId));

        assertThat(service.checkReuse("replayed-token")).contains(userId);
    }

    private JwtTokenService newService(KeyPair keyPair) throws IOException {
        Path privateKeyPath = tempDir.resolve("private-" + System.nanoTime() + ".pem");
        Path publicKeyPath = tempDir.resolve("public-" + System.nanoTime() + ".pem");
        Files.writeString(privateKeyPath, toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(publicKeyPath, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);

        JwtProperties jwtProperties = new JwtProperties(
                privateKeyPath.toString(),
                publicKeyPath.toString(),
                ACCESS_TOKEN_EXPIRY_MINUTES,
                REFRESH_TOKEN_EXPIRY_DAYS);
        JwtTokenService service = new JwtTokenService(jwtProperties, tokenStore);
        service.loadKeys();
        return service;
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String toPem(String label, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der);
        return "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
    }
}
