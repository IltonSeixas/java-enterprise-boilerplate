package com.enterprise.boilerplate.infrastructure.cache;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Component
public class RedisTokenStore {

    // Atomically redeems a refresh token in a single round trip, closing the
    // check-then-act race between checkReuse/resolveUserIdFromRefreshToken/
    // revokeRefreshToken that three separate commands would otherwise leave open:
    // two concurrent replays of the same stolen token could both read the token
    // as still valid before either finished deleting it.
    //
    // KEYS[1] = used-refresh:<token> (tombstone)   KEYS[2] = refresh:<token>
    // ARGV[1] = tombstone TTL (ms)
    //
    // Returns {"REUSED", userId} if a tombstone already exists (token was already
    // rotated out — a replay), {"OK", userId, userRefreshKeyPrefix} if the token
    // was live and is now atomically consumed, or {"INVALID"} if neither exists.
    private static final RedisScript<List> REDEEM_SCRIPT = RedisScript.of(
            """
            local usedVal = redis.call('GET', KEYS[1])
            if usedVal then
              return {'REUSED', usedVal}
            end
            local userId = redis.call('GET', KEYS[2])
            if not userId then
              return {'INVALID'}
            end
            redis.call('DEL', KEYS[2])
            redis.call('SET', KEYS[1], userId, 'PX', ARGV[1])
            return {'OK', userId}
            """,
            List.class);

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

    /**
     * Atomically redeems the refresh token identified by {@code refreshKey}: if a
     * reuse tombstone already exists at {@code usedKey}, returns it (signaling reuse)
     * without touching state; otherwise, if {@code refreshKey} holds a live userId,
     * deletes it and writes the tombstone in the same Redis round trip, then returns
     * that userId. Returns empty if the token was never issued or already expired.
     */
    @CircuitBreaker(name = "redis")
    public RedeemResult redeemRefreshToken(String usedKey, String refreshKey, long tombstoneTtlSeconds) {
        @SuppressWarnings("unchecked")
        List<String> result = redisTemplate.execute(
                REDEEM_SCRIPT,
                List.of(usedKey, refreshKey),
                String.valueOf(tombstoneTtlSeconds * 1000));

        if (result == null || result.isEmpty()) {
            return RedeemResult.invalid();
        }
        return switch (result.get(0)) {
            case "REUSED" -> RedeemResult.reused(result.get(1));
            case "OK" -> RedeemResult.redeemed(result.get(1));
            default -> RedeemResult.invalid();
        };
    }

    public sealed interface RedeemResult {
        record Reused(String userId) implements RedeemResult {}
        record Redeemed(String userId) implements RedeemResult {}
        record Invalid() implements RedeemResult {}

        static RedeemResult reused(String userId) { return new Reused(userId); }
        static RedeemResult redeemed(String userId) { return new Redeemed(userId); }
        static RedeemResult invalid() { return new Invalid(); }
    }

    @CircuitBreaker(name = "redis")
    public void deleteByPattern(String pattern, Consumer<String> perKey) {
        redisTemplate.executeWithStickyConnection(connection -> {
            ScanOptions opts = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (var cursor = connection.keyCommands().scan(opts)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    perKey.accept(key);
                }
            }
            return null;
        });
    }
}
