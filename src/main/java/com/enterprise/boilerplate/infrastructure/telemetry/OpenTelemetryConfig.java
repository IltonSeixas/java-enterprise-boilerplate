package com.enterprise.boilerplate.infrastructure.telemetry;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfig {

    @Bean
    public MeterRegistryCustomizer<PrometheusMeterRegistry> prometheusCustomizer(
            @Value("${spring.application.name:java-enterprise-boilerplate}") String appName) {
        return registry -> registry.config()
                .commonTags("service.name", appName);
    }
}
