package com.enterprise.boilerplate.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String privateKeyPath,
        @NotBlank String publicKeyPath,
        @Positive long accessTokenExpiryMinutes,
        @Positive long refreshTokenExpiryDays) {
}
