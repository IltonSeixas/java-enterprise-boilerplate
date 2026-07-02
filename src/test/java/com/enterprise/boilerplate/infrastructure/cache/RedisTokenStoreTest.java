package com.enterprise.boilerplate.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisTokenStoreTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private RedisTokenStore store;

    @Test
    void set_delegatesToRedisWithCorrectTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        store.set("refresh:abc", "user-id", 86400L);

        verify(valueOps).set("refresh:abc", "user-id", Duration.ofSeconds(86400L));
    }

    @Test
    void get_returnsValueWhenPresent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:abc")).thenReturn("user-id");

        Optional<String> result = store.get("refresh:abc");

        assertThat(result).contains("user-id");
    }

    @Test
    void get_returnsEmptyWhenKeyAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("refresh:missing")).thenReturn(null);

        Optional<String> result = store.get("refresh:missing");

        assertThat(result).isEmpty();
    }

    @Test
    void delete_delegatesToRedis() {
        store.delete("refresh:abc");

        verify(redisTemplate).delete("refresh:abc");
    }
}
