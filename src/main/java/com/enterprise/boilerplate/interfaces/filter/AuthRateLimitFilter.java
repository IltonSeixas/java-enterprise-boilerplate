package com.enterprise.boilerplate.interfaces.filter;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import com.enterprise.boilerplate.config.properties.RateLimitProperties;
import com.enterprise.boilerplate.application.ratelimit.SlidingWindowRateLimiter;
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

/**
 * Per-IP rate limiter for authentication endpoints.
 *
 * Delegates the actual counting to {@link SlidingWindowRateLimiter}, shared with the
 * gRPC edge's {@code GrpcRateLimitInterceptor}; this class only owns the REST-specific
 * concerns of path matching, client IP resolution from the servlet request, and the
 * 429 response body.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_REQUESTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth";
    private static final String RATE_LIMIT_KEY_PREFIX = "rl:auth:";

    private final boolean trustForwardedHeaders;
    private final SlidingWindowRateLimiter rateLimiter;

    public AuthRateLimitFilter(RateLimitProperties rateLimitProperties, RateLimitPort rateLimitPort) {
        this.trustForwardedHeaders = rateLimitProperties.trustForwardedHeaders();
        this.rateLimiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
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

        if (rateLimiter.isRateLimited(RATE_LIMIT_KEY_PREFIX + ip)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"statusCode\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
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
}
