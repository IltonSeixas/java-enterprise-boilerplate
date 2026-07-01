package com.enterprise.boilerplate.infrastructure.health;

import com.enterprise.boilerplate.config.properties.JwtProperties;
import com.enterprise.boilerplate.infrastructure.security.Ed25519PemKeyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class JwtKeysHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(JwtKeysHealthIndicator.class);

    private final Path privateKeyPath;
    private final Path publicKeyPath;

    public JwtKeysHealthIndicator(JwtProperties jwtProperties) {
        this.privateKeyPath = Path.of(jwtProperties.privateKeyPath());
        this.publicKeyPath = Path.of(jwtProperties.publicKeyPath());
    }

    @Override
    public Health health() {
        try {
            Ed25519PemKeyLoader.loadPrivateKey(privateKeyPath);
            Ed25519PemKeyLoader.loadPublicKey(publicKeyPath);
            return Health.up().build();
        } catch (RuntimeException e) {
            log.error("JWT key health check failed", e);
            return Health.down().withDetail("error", "JWT keys unavailable").build();
        }
    }
}
