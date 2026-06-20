package com.enterprise.boilerplate.infrastructure.health;

import com.enterprise.boilerplate.config.properties.JwtProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.health.contributor.Status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtKeysHealthIndicatorTest {

    private static final long ACCESS_TOKEN_EXPIRY_MINUTES = 15L;
    private static final long REFRESH_TOKEN_EXPIRY_DAYS = 7L;

    @TempDir
    private Path tempDir;

    @Test
    void health_withValidKeyPair_returnsUp() throws Exception {
        JwtKeysHealthIndicator indicator = newIndicator(generateKeyPair());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void health_withMissingKeyFiles_returnsDown() {
        JwtProperties jwtProperties = new JwtProperties(
                tempDir.resolve("missing-private.pem").toString(),
                tempDir.resolve("missing-public.pem").toString(),
                ACCESS_TOKEN_EXPIRY_MINUTES,
                REFRESH_TOKEN_EXPIRY_DAYS);

        JwtKeysHealthIndicator indicator = new JwtKeysHealthIndicator(jwtProperties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void health_withCorruptKeyFile_returnsDown() throws IOException {
        Path privateKeyPath = tempDir.resolve("corrupt-private.pem");
        Path publicKeyPath = tempDir.resolve("corrupt-public.pem");
        Files.writeString(privateKeyPath, "-----BEGIN PRIVATE KEY-----\nnot-valid-base64!!\n-----END PRIVATE KEY-----\n");
        Files.writeString(publicKeyPath, "-----BEGIN PUBLIC KEY-----\nnot-valid-base64!!\n-----END PUBLIC KEY-----\n");

        JwtProperties jwtProperties = new JwtProperties(
                privateKeyPath.toString(),
                publicKeyPath.toString(),
                ACCESS_TOKEN_EXPIRY_MINUTES,
                REFRESH_TOKEN_EXPIRY_DAYS);

        JwtKeysHealthIndicator indicator = new JwtKeysHealthIndicator(jwtProperties);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    private JwtKeysHealthIndicator newIndicator(KeyPair keyPair) throws IOException {
        Path privateKeyPath = tempDir.resolve("private-" + System.nanoTime() + ".pem");
        Path publicKeyPath = tempDir.resolve("public-" + System.nanoTime() + ".pem");
        Files.writeString(privateKeyPath, toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(publicKeyPath, toPem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);

        JwtProperties jwtProperties = new JwtProperties(
                privateKeyPath.toString(),
                publicKeyPath.toString(),
                ACCESS_TOKEN_EXPIRY_MINUTES,
                REFRESH_TOKEN_EXPIRY_DAYS);
        return new JwtKeysHealthIndicator(jwtProperties);
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
