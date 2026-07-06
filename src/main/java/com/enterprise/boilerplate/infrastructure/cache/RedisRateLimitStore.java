package com.enterprise.boilerplate.infrastructure.cache;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisRateLimitStore implements RateLimitPort {

    // Atomic fixed-window counter using INCR + EXPIRE.
    // If the key does not exist, INCR creates it at 1; the EXPIRE is set only on
    // the first call within the window so subsequent increments reuse the same
    // window boundary. This gives a fixed-window semantic per replica-shared key.
    //
    // Script returns the current counter value after incrementing so the caller
    // can decide whether to allow or reject the request in a single round-trip.
    private static final RedisScript<Long> INCREMENT_SCRIPT = RedisScript.of(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRateLimitStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Atomically increments the counter for {@code key} and returns the new value.
     * The TTL is set only when the counter is created (first request in the window).
     * Falls back to -1 on Redis failure so callers can degrade gracefully.
     */
    @CircuitBreaker(name = "redis", fallbackMethod = "incrementFallback")
    public long increment(String key, Duration window) {
        Long value = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(key),
                String.valueOf(window.toMillis()));
        return value != null ? value : 1L;
    }

    @SuppressWarnings("unused")
    private long incrementFallback(String key, Duration window, Throwable t) {
        return -1L;
    }
}
