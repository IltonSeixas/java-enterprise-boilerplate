package com.enterprise.boilerplate.infrastructure.security;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveActionRateLimitFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static SensitiveActionRateLimitFilter filterWith(RateLimitPort store) {
        return new SensitiveActionRateLimitFilter(store);
    }

    private static RateLimitPort counterStore() {
        RateLimitPort store = mock(RateLimitPort.class);
        Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
        when(store.increment(anyString(), any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        });
        return store;
    }

    private static void authenticateAs(String userId) {
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldNotFilter_returnsTrue_forUnrelatedPaths() {
        var filter = filterWith(mock(RateLimitPort.class));
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setServletPath("/api/v1/users/me");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsFalse_forPasswordChange() {
        var filter = filterWith(mock(RateLimitPort.class));
        var request = new MockHttpServletRequest("PUT", "/api/v1/users/me/password");
        request.setServletPath("/api/v1/users/me/password");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_returnsFalse_forRoleChange() {
        var filter = filterWith(mock(RateLimitPort.class));
        var request = new MockHttpServletRequest("PUT", "/api/v1/users/abc-123/role");
        request.setServletPath("/api/v1/users/abc-123/role");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_returnsTrue_forGetOnRolePath() {
        var filter = filterWith(mock(RateLimitPort.class));
        var request = new MockHttpServletRequest("GET", "/api/v1/users/abc-123/role");
        request.setServletPath("/api/v1/users/abc-123/role");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void doFilterInternal_allowsRequestsUnderLimit() throws Exception {
        authenticateAs("user-1");
        var filter = filterWith(counterStore());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < SensitiveActionRateLimitFilter.MAX_REQUESTS; i++) {
            var request = new MockHttpServletRequest("PUT", "/api/v1/users/me/password");
            request.setServletPath("/api/v1/users/me/password");
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void doFilterInternal_blocksRequestsOverLimit() throws Exception {
        authenticateAs("user-2");
        var filter = filterWith(counterStore());
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < SensitiveActionRateLimitFilter.MAX_REQUESTS + 1; i++) {
            var request = new MockHttpServletRequest("PUT", "/api/v1/users/me/password");
            request.setServletPath("/api/v1/users/me/password");
            lastResponse = new MockHttpServletResponse();
            filter.doFilter(request, lastResponse, chain);
        }

        assertThat(lastResponse.getStatus()).isEqualTo(429);
        assertThat(lastResponse.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void doFilterInternal_ratesLimitsIndependentlyPerUser() throws Exception {
        var store = counterStore();
        var filter = filterWith(store);
        FilterChain chain = mock(FilterChain.class);

        authenticateAs("user-a");
        for (int i = 0; i < SensitiveActionRateLimitFilter.MAX_REQUESTS; i++) {
            var request = new MockHttpServletRequest("PUT", "/api/v1/users/me/password");
            request.setServletPath("/api/v1/users/me/password");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        authenticateAs("user-b");
        var request = new MockHttpServletRequest("PUT", "/api/v1/users/me/password");
        request.setServletPath("/api/v1/users/me/password");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void doFilterInternal_passesThrough_whenNoAuthenticatedPrincipal() throws Exception {
        var filter = filterWith(mock(RateLimitPort.class));
        FilterChain chain = mock(FilterChain.class);
        var request = new MockHttpServletRequest("PUT", "/api/v1/users/me/password");
        request.setServletPath("/api/v1/users/me/password");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
