package com.enterprise.boilerplate.application.ratelimit;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-key fixed-window rate limiter shared by the REST and gRPC edge adapters.
 *
 * Primary: distributed counter via {@link RateLimitPort} (Redis) — effective across
 * all replicas. Fallback: in-process fixed window — used when Redis is unavailable
 * (circuit open). The fallback is intentionally permissive under Redis failure rather
 * than blocking legitimate traffic; operators are alerted via the Redis circuit
 * breaker metrics when this path is active.
 *
 * One instance is owned per calling adapter (REST filter, gRPC interceptor) so each
 * keeps an independent local fallback window and key prefix; the distributed counter
 * in Redis is shared and namespaced by the caller-supplied key.
 */
public final class SlidingWindowRateLimiter {

    private final RateLimitPort rateLimitPort;
    private final int maxRequests;
    private final Duration window;
    private final long sweepIntervalMillis;
    private final int maxTrackedClients;

    private record Window(AtomicInteger count, long windowStart) {}
    private final ConcurrentHashMap<String, Window> localWindows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.currentTimeMillis());

    public SlidingWindowRateLimiter(RateLimitPort rateLimitPort, int maxRequests, Duration window) {
        this(rateLimitPort, maxRequests, window, window.toMillis() * 5, 100_000);
    }

    SlidingWindowRateLimiter(RateLimitPort rateLimitPort, int maxRequests, Duration window,
                             long sweepIntervalMillis, int maxTrackedClients) {
        this.rateLimitPort = rateLimitPort;
        this.maxRequests = maxRequests;
        this.window = window;
        this.sweepIntervalMillis = sweepIntervalMillis;
        this.maxTrackedClients = maxTrackedClients;
    }

    public boolean isRateLimited(String key) {
        long count = rateLimitPort.increment(key, window);
        if (count == -1L) {
            // Redis unavailable — fall back to in-process counter.
            return localIncrement(key) > maxRequests;
        }
        return count > maxRequests;
    }

    private long localIncrement(String key) {
        long now = System.currentTimeMillis();
        sweepStaleEntries(now);

        Window w = localWindows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart() >= window.toMillis()) {
                return new Window(new AtomicInteger(0), now);
            }
            return existing;
        });

        return w.count().incrementAndGet();
    }

    private void sweepStaleEntries(long now) {
        long previousSweep = lastSweep.get();
        if (now - previousSweep < sweepIntervalMillis) {
            return;
        }
        if (!lastSweep.compareAndSet(previousSweep, now)) {
            return;
        }
        localWindows.entrySet().removeIf(e -> now - e.getValue().windowStart() >= window.toMillis());
        if (localWindows.size() > maxTrackedClients) {
            localWindows.clear();
        }
    }
}
