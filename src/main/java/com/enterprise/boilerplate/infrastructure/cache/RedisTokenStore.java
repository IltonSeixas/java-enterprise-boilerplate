package com.enterprise.boilerplate.infrastructure.cache;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

@Component
public class RedisTokenStore {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTokenStore(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @CircuitBreaker(name = "redis")
    public void set(String key, String value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    @CircuitBreaker(name = "redis")
    @Retry(name = "redis-read")
    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @CircuitBreaker(name = "redis")
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @CircuitBreaker(name = "redis")
    public void deleteByPattern(String pattern, Consumer<String> perKey) {
        redisTemplate.executeWithStickyConnection(connection -> {
            ScanOptions opts = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (var cursor = connection.keyCommands().scan(opts)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next());
                    perKey.accept(key);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }
}
