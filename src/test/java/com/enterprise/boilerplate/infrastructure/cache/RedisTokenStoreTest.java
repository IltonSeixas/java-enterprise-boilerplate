package com.enterprise.boilerplate.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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

    @Test
    void redeemRefreshToken_returnsReused_whenScriptReportsExistingTombstone() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(List.of("REUSED", "user-id"));

        var result = store.redeemRefreshToken("used-refresh:tok", "refresh:tok", 300L);

        assertThat(result).isEqualTo(new RedisTokenStore.RedeemResult.Reused("user-id"));
    }

    @Test
    void redeemRefreshToken_returnsRedeemed_whenScriptConsumesLiveToken() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(List.of("OK", "user-id"));

        var result = store.redeemRefreshToken("used-refresh:tok", "refresh:tok", 300L);

        assertThat(result).isEqualTo(new RedisTokenStore.RedeemResult.Redeemed("user-id"));
    }

    @Test
    void redeemRefreshToken_returnsInvalid_whenScriptFindsNeitherKey() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(List.of("INVALID"));

        var result = store.redeemRefreshToken("used-refresh:tok", "refresh:tok", 300L);

        assertThat(result).isEqualTo(new RedisTokenStore.RedeemResult.Invalid());
    }

    @Test
    void redeemRefreshToken_returnsInvalid_whenScriptResultIsNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(null);

        var result = store.redeemRefreshToken("used-refresh:tok", "refresh:tok", 300L);

        assertThat(result).isEqualTo(new RedisTokenStore.RedeemResult.Invalid());
    }

    @Test
    void redeemRefreshToken_passesTombstoneTtlInMillis() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString()))
                .thenAnswer(invocation -> {
                    String millisArg = invocation.getArgument(2);
                    assertThat(millisArg).isEqualTo("300000");
                    return List.of("INVALID");
                });

        store.redeemRefreshToken("used-refresh:tok", "refresh:tok", 300L);
    }

    @Test
    void deleteByPattern_invokesCallbackForEachScannedKey() {
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);

        when(redisTemplate.executeWithStickyConnection(any())).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(keyCommands.scan(any(org.springframework.data.redis.core.ScanOptions.class))).thenReturn(cursor);

        List<byte[]> scannedKeys = List.of(
                "user-refresh:u1:tok-a".getBytes(StandardCharsets.UTF_8),
                "user-refresh:u1:tok-b".getBytes(StandardCharsets.UTF_8));
        Iterator<byte[]> iterator = scannedKeys.iterator();
        when(cursor.hasNext()).thenAnswer(inv -> iterator.hasNext());
        when(cursor.next()).thenAnswer(inv -> iterator.next());

        List<String> visited = new ArrayList<>();
        Consumer<String> perKey = visited::add;

        store.deleteByPattern("user-refresh:u1:*", perKey);

        assertThat(visited).containsExactly("user-refresh:u1:tok-a", "user-refresh:u1:tok-b");
    }

    @Test
    void deleteByPattern_closesCursor() {
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);

        when(redisTemplate.executeWithStickyConnection(any())).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(keyCommands.scan(any(org.springframework.data.redis.core.ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);

        store.deleteByPattern("user-refresh:u1:*", key -> { });

        verify(cursor).close();
    }

    @Test
    void deleteByPattern_passesPatternToScanOptions() {
        RedisConnection connection = mock(RedisConnection.class);
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        @SuppressWarnings("unchecked")
        Cursor<byte[]> cursor = mock(Cursor.class);

        when(redisTemplate.executeWithStickyConnection(any())).thenAnswer(invocation -> {
            RedisCallback<?> callback = invocation.getArgument(0);
            return callback.doInRedis(connection);
        });
        when(connection.keyCommands()).thenReturn(keyCommands);
        when(keyCommands.scan(any(org.springframework.data.redis.core.ScanOptions.class))).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(false);

        store.deleteByPattern("user-refresh:u1:*", key -> { });

        ArgumentCaptor<org.springframework.data.redis.core.ScanOptions> optionsCaptor =
                ArgumentCaptor.forClass(org.springframework.data.redis.core.ScanOptions.class);
        verify(keyCommands).scan(optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().getPattern()).isEqualTo("user-refresh:u1:*");
    }
}
