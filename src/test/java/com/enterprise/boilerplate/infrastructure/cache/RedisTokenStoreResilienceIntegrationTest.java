package com.enterprise.boilerplate.infrastructure.cache;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisTokenStoreResilienceIntegrationTest {

    @DynamicPropertySource
    static void unreachableRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", () -> "redis://localhost:1");
        registry.add("spring.data.redis.connect-timeout", () -> "100ms");
        registry.add("spring.data.redis.timeout", () -> "100ms");
    }

    @Autowired
    private RedisTokenStore tokenStore;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void circuitBreaker_opens_afterRepeatedRedisFailures() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        for (int i = 0; i < 10; i++) {
            try {
                tokenStore.get("any-key");
            } catch (Exception expected) {
                // connection failure is expected on every attempt against an unreachable host
            }
        }

        await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN));

        assertThatThrownBy(() -> tokenStore.get("any-key"))
                .isInstanceOf(CallNotPermittedException.class);
    }
}
