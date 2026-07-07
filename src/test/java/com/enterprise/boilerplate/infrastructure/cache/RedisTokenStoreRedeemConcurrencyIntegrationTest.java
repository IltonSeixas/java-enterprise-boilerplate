package com.enterprise.boilerplate.infrastructure.cache;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the fix for the refresh-token rotation race: {@link RedisTokenStore#redeemRefreshToken}
 * must let exactly one of several concurrent replays of the same token win, because
 * a single atomic Lua script — not a check-then-act sequence of separate Redis calls —
 * backs the operation.
 */
@Tag("integration")
@Testcontainers
class RedisTokenStoreRedeemConcurrencyIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;

    @BeforeAll
    static void startConnectionFactory() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
    }

    @AfterAll
    static void stopConnectionFactory() {
        connectionFactory.destroy();
    }

    private static RedisTokenStore newStore() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return new RedisTokenStore(template);
    }

    @Test
    void redeemRefreshToken_underConcurrentReplay_exactlyOneCallerRedeemsIt() throws InterruptedException {
        RedisTokenStore store = newStore();
        String userId = "user-42";
        String refreshKey = "refresh:concurrent-token";
        String usedKey = "used-refresh:concurrent-token";
        store.set(refreshKey, userId, 3600L);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger redeemedCount = new AtomicInteger();
        AtomicInteger reusedCount = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                RedisTokenStore.RedeemResult result = store.redeemRefreshToken(usedKey, refreshKey, 300L);
                switch (result) {
                    case RedisTokenStore.RedeemResult.Redeemed ignored -> redeemedCount.incrementAndGet();
                    case RedisTokenStore.RedeemResult.Reused ignored -> reusedCount.incrementAndGet();
                    case RedisTokenStore.RedeemResult.Invalid ignored -> { }
                }
            });
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // Exactly one of the twenty concurrent replays redeems the token; every other
        // caller observes the tombstone the winner wrote and is correctly told REUSED.
        assertThat(redeemedCount.get()).isEqualTo(1);
        assertThat(reusedCount.get()).isEqualTo(threads - 1);
    }
}
