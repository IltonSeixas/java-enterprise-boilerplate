package com.enterprise.boilerplate.interfaces.filter;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MILLIS = 60_000L;
    private static final long SWEEP_INTERVAL_MILLIS = 5 * WINDOW_MILLIS;
    private static final int MAX_TRACKED_CLIENTS = 100_000;
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth";

    private final boolean trustForwardedHeaders;

    private record Window(AtomicInteger count, long windowStart) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.currentTimeMillis());

    public AuthRateLimitFilter(RateLimitProperties rateLimitProperties) {
        this.trustForwardedHeaders = rateLimitProperties.trustForwardedHeaders();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        sweepStaleEntries(now);

        String ip = resolveClientIp(request);

        Window window = windows.compute(ip, (key, existing) -> {
            if (existing == null || now - existing.windowStart() >= WINDOW_MILLIS) {
                return new Window(new AtomicInteger(0), now);
            }
            return existing;
        });

        if (window.count().incrementAndGet() > MAX_REQUESTS) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"statusCode":429,"error":"Too Many Requests","message":"Rate limit exceeded. Try again later."}
                    """.strip());
            return;
        }

        chain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // The leftmost entry is client-supplied and trivially spoofable; the
                // rightmost entry is the one appended by our own trusted reverse proxy.
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
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart() >= WINDOW_MILLIS);
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.clear();
        }
    }
}
