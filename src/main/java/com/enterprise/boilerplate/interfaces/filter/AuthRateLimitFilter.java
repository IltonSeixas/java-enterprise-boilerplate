package com.enterprise.boilerplate.interfaces.filter;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import com.enterprise.boilerplate.config.properties.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-IP rate limiter for authentication endpoints.
 *
 * Primary: distributed counter in Redis — effective across all replicas.
 * Fallback: in-process fixed window — used when Redis is unavailable (circuit
 * open). The fallback is intentionally permissive under Redis failure rather
 * than blocking legitimate traffic; operators are alerted via the redis circuit
 * breaker metrics when this path is active.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_REQUESTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private static final long SWEEP_INTERVAL_MILLIS = WINDOW.toMillis() * 5;
    private static final int MAX_TRACKED_CLIENTS = 100_000;
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth";
    private static final String RATE_LIMIT_KEY_PREFIX = "rl:auth:";

    private final boolean trustForwardedHeaders;
    private final RateLimitPort rateLimitPort;

    // In-process fallback used only when Redis circuit is open.
    private record Window(AtomicInteger count, long windowStart) {}
    private final ConcurrentHashMap<String, Window> localWindows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.currentTimeMillis());

    public AuthRateLimitFilter(RateLimitProperties rateLimitProperties, RateLimitPort rateLimitPort) {
        this.trustForwardedHeaders = rateLimitProperties.trustForwardedHeaders();
        this.rateLimitPort = rateLimitPort;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = resolveClientIp(request);

        if (isRateLimited(ip)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"statusCode\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimited(String ip) {
        long count = rateLimitPort.increment(RATE_LIMIT_KEY_PREFIX + ip, WINDOW);

        if (count == -1L) {
            // Redis unavailable — fall back to in-process counter.
            return localIncrement(ip) > MAX_REQUESTS;
        }

        return count > MAX_REQUESTS;
    }

    private long localIncrement(String ip) {
        long now = System.currentTimeMillis();
        sweepStaleEntries(now);

        Window window = localWindows.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart() >= WINDOW.toMillis()) {
                return new Window(new AtomicInteger(0), now);
            }
            return existing;
        });

        return window.count().incrementAndGet();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // The rightmost hop is appended by our own trusted reverse proxy.
                String[] hops = forwarded.split(",");
                return hops[hops.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void sweepStaleEntries(long now) {
        long previousSweep = lastSweep.get();
        if (now - previousSweep < SWEEP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastSweep.compareAndSet(previousSweep, now)) {
            return;
        }
        localWindows.entrySet().removeIf(e -> now - e.getValue().windowStart() >= WINDOW.toMillis());
        if (localWindows.size() > MAX_TRACKED_CLIENTS) {
            localWindows.clear();
        }
    }
}
