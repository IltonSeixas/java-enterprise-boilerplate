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

import static org.assertj.core.api.Assertions.assertThat;

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
                .contains(user.getId().toString());
    }

    @Test
    void validateAccessToken_withTokenSignedByDifferentKeyPair_returnsEmpty() throws Exception {
        JwtTokenService signingService = newService(generateKeyPair());
        JwtTokenService verifyingService = newService(generateKeyPair());

        String token = signingService.issueAccessToken(user);

        assertThat(verifyingService.validateAccessToken(token)).isEmpty();
    }

    @Test
    void extractRole_returnsRoleClaimFromIssuedToken() throws Exception {
        JwtTokenService service = newService(generateKeyPair());

        String token = service.issueAccessToken(user);

        assertThat(service.extractRole(token)).isEqualTo("ADMIN");
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
        return new JwtTokenService(jwtProperties, tokenStore);
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
