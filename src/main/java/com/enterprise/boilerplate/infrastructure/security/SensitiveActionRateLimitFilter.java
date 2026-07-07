package com.enterprise.boilerplate.infrastructure.security;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import com.enterprise.boilerplate.application.ratelimit.SlidingWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Per-user rate limiter for sensitive authenticated account actions: password change
 * and role change. Both require a valid access token, so this is not a brute-force
 * vector in the same sense as the unauthenticated auth endpoints — but a holder of a
 * (possibly stolen) access token could otherwise hammer these endpoints without limit,
 * either to brute-force the current password before the token expires, or simply to
 * force repeated expensive Argon2id verification as a cost-amplification/DoS vector.
 *
 * Keyed by authenticated user id (not IP, since this filter only ever runs for
 * requests that already passed {@link JwtAuthenticationFilter}) via the same shared
 * {@link SlidingWindowRateLimiter} used by the REST edge's {@code AuthRateLimitFilter}
 * and the gRPC edge's rate limit interceptor.
 *
 * Registered explicitly in {@code SecurityConfig} via
 * {@code addFilterAfter(..., JwtAuthenticationFilter.class)} rather than picked up as
 * a generic {@code @Component}-scanned servlet filter — the latter would run before
 * Spring Security's chain populates the {@code SecurityContextHolder}, leaving this
 * filter with no authenticated principal to key on.
 */
@Component
public class SensitiveActionRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_REQUESTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private static final String RATE_LIMIT_KEY_PREFIX = "rl:sensitive:";

    // servletPath -> allowed HTTP method; both must match for a request to be limited.
    private static final Map<String, HttpMethod> RATE_LIMITED_ROUTES = Map.of(
            "/api/v1/users/me/password", HttpMethod.PUT
    );
    private static final String ROLE_PATH_SUFFIX = "/role";

    private final SlidingWindowRateLimiter rateLimiter;

    public SensitiveActionRateLimitFilter(RateLimitPort rateLimitPort) {
        this.rateLimiter = new SlidingWindowRateLimiter(rateLimitPort, MAX_REQUESTS, WINDOW);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isRateLimitedRoute(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            // No authenticated principal to key on — let the request proceed to the
            // normal auth checks, which will reject it with 401 downstream.
            chain.doFilter(request, response);
            return;
        }

        String userId = String.valueOf(authentication.getPrincipal());
        if (rateLimiter.isRateLimited(RATE_LIMIT_KEY_PREFIX + userId)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"statusCode\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Try again later.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimitedRoute(HttpServletRequest request) {
        String path = request.getServletPath();
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        if (method.equals(RATE_LIMITED_ROUTES.get(path))) {
            return true;
        }

        return method == HttpMethod.PUT
                && path.startsWith("/api/v1/users/")
                && path.endsWith(ROLE_PATH_SUFFIX);
    }
}
