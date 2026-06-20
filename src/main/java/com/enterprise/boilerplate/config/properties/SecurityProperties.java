package com.enterprise.boilerplate.config.properties;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "security")
public record SecurityProperties(@NestedConfigurationProperty Cors cors) {

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }
}
