package com.enterprise.boilerplate.interfaces.ratelimit;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlidingWindowRateLimiterTest {

    @Mock private RateLimitPort rateLimitPort;

    private static final int MAX_REQUESTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Test
    void isRateLimited_returnsFalse_whenCountAtOrBelowLimit() {
        var limiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
        when(rateLimitPort.increment(anyString(), any())).thenReturn((long) MAX_REQUESTS);

        assertThat(limiter.isRateLimited("key")).isFalse();
    }

    @Test
    void isRateLimited_returnsTrue_whenCountExceedsLimit() {
        var limiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
        when(rateLimitPort.increment(anyString(), any())).thenReturn((long) MAX_REQUESTS + 1);

        assertThat(limiter.isRateLimited("key")).isTrue();
    }

    @Test
    void isRateLimited_forwardsWindowToPort() {
        var limiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
        when(rateLimitPort.increment(anyString(), any())).thenReturn(1L);

        limiter.isRateLimited("some-key");

        org.mockito.Mockito.verify(rateLimitPort).increment("some-key", WINDOW);
    }

    @Test
    void isRateLimited_fallsBackToLocalCounter_whenPortUnavailable() {
        var limiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
        when(rateLimitPort.increment(anyString(), any())).thenReturn(-1L);

        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(limiter.isRateLimited("fallback-key")).isFalse();
        }
        assertThat(limiter.isRateLimited("fallback-key")).isTrue();
    }

    @Test
    void isRateLimited_localFallback_tracksKeysIndependently() {
        var limiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
        when(rateLimitPort.increment(anyString(), any())).thenReturn(-1L);

        for (int i = 0; i < MAX_REQUESTS; i++) {
            limiter.isRateLimited("key-a");
        }
        // key-b has its own independent counter, unaffected by key-a's exhaustion.
        assertThat(limiter.isRateLimited("key-b")).isFalse();
    }

    @Test
    void isRateLimited_localFallback_resetsAfterWindowElapses() {
        var shortWindow = Duration.ofMillis(50);
        var limiter = new SlidingWindowRateLimiter(rateLimitPort, 1, shortWindow, 1, 100_000);
        when(rateLimitPort.increment(anyString(), any())).thenReturn(-1L);

        assertThat(limiter.isRateLimited("expiring-key")).isFalse();
        assertThat(limiter.isRateLimited("expiring-key")).isTrue();

        await(shortWindow.toMillis() + 20);

        assertThat(limiter.isRateLimited("expiring-key")).isFalse();
    }

    @Test
    void isRateLimited_localFallback_clearsAllEntries_whenTrackedClientsExceedsCap() throws InterruptedException {
        var limiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW, 0, 2);
        when(rateLimitPort.increment(anyString(), any())).thenReturn(-1L);

        limiter.isRateLimited("key-1");
        Thread.sleep(2);
        limiter.isRateLimited("key-2");
        Thread.sleep(2);
        // This sweep runs (sweepIntervalMillis=0), sees 2 tracked clients > maxTrackedClients=2 is false,
        // so add a third to push past the cap and trigger the clear.
        limiter.isRateLimited("key-3");

        // After the cap-triggered clear, "key-1" starts a fresh window and is not rate-limited.
        for (int i = 0; i < MAX_REQUESTS; i++) {
            assertThat(limiter.isRateLimited("key-1")).isFalse();
        }
    }

    private static void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
