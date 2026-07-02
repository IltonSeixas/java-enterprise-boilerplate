package com.enterprise.boilerplate.infrastructure.cache;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitStoreTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private RedisRateLimitStore store;

    @BeforeEach
    void setUp() {
        store = new RedisRateLimitStore(redisTemplate);

        // Register a circuit breaker named "redis" so the @CircuitBreaker AOP aspect
        // can find it. Use a config that requires many failures to open so individual
        // tests don't inadvertently trip each other.
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(100)
                        .minimumNumberOfCalls(100)
                        .build());
        registry.circuitBreaker("redis");
    }

    @Test
    void increment_returnsScriptResult_whenRedisResponds() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(3L);

        long result = store.increment("rl:auth:1.2.3.4", Duration.ofMinutes(1));

        assertThat(result).isEqualTo(3L);
    }

    @Test
    void increment_returnsOne_whenScriptReturnsNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(null);

        long result = store.increment("rl:auth:1.2.3.4", Duration.ofMinutes(1));

        assertThat(result).isEqualTo(1L);
    }

    @Test
    void increment_passesWindowMillisAsArgument() {
        Duration window = Duration.ofSeconds(90);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString()))
                .thenAnswer(invocation -> {
                    String millis = invocation.getArgument(2);
                    assertThat(millis).isEqualTo(String.valueOf(window.toMillis()));
                    return 1L;
                });

        store.increment("key", window);
    }

    @Test
    void incrementFallback_returnsNegativeOne() {
        // @CircuitBreaker is only active on an AOP proxy; calling the fallback method
        // directly verifies the contract: any Redis failure degrades to -1.
        long fallback = (long) ReflectionTestUtils.invokeMethod(
                store, "incrementFallback", "key", Duration.ofMinutes(1), new RuntimeException("Redis down"));

        assertThat(fallback).isEqualTo(-1L);
    }
}
