package com.enterprise.boilerplate.interfaces.filter;

import com.enterprise.boilerplate.application.port.out.RateLimitPort;
import com.enterprise.boilerplate.config.properties.RateLimitProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimitFilterTest {

    private static AuthRateLimitFilter filterWith(boolean trust, RateLimitPort store) {
        return new AuthRateLimitFilter(new RateLimitProperties(trust), store);
    }

    /**
     * Returns a store mock whose counter increments per call for a given IP key,
     * simulating the Redis INCR behavior. The counter is shared across all calls
     * with the same key prefix to mirror the real Redis behavior within a test.
     */
    private static RateLimitPort counterStore() {
        RateLimitPort store = mock(RateLimitPort.class);
        AtomicLong counter = new AtomicLong(0);
        when(store.increment(anyString(), any())).thenAnswer(inv -> counter.incrementAndGet());
        return store;
    }

    private static RateLimitPort unavailableStore() {
        RateLimitPort store = mock(RateLimitPort.class);
        when(store.increment(anyString(), any())).thenReturn(-1L);
        return store;
    }

    @Test
    void shouldNotFilter_returnsTrue_forNonAuthPaths() {
        var filter = filterWith(false, mock(RateLimitPort.class));
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setServletPath("/api/v1/users/me");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsFalse_forAuthPaths() {
        var filter = filterWith(false, mock(RateLimitPort.class));
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_usesNormalizedServletPath_notRawUri() {
        var filter = filterWith(false, mock(RateLimitPort.class));
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login;jsessionid=abc");
        request.setServletPath("/api/v1/auth/login");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilterInternal_allowsRequestsUnderLimit_viaRedis() throws Exception {
        var store = counterStore();
        var filter = filterWith(false, store);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.setRemoteAddr("203.0.113.10");
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
        }

        verify(chain, times(AuthRateLimitFilter.MAX_REQUESTS)).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilterInternal_blocksRequestsOverLimit_viaRedis() throws Exception {
        var store = counterStore();
        var filter = filterWith(false, store);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS + 1; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.setRemoteAddr("203.0.113.20");
            lastResponse = new MockHttpServletResponse();

            filter.doFilter(request, lastResponse, chain);
        }

        assertThat(lastResponse.getStatus()).isEqualTo(429);
        assertThat(lastResponse.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void doFilterInternal_fallsBackToLocalCounter_whenRedisUnavailable() throws Exception {
        var store = unavailableStore();
        var filter = filterWith(false, store);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS + 1; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.setRemoteAddr("203.0.113.30");
            lastResponse = new MockHttpServletResponse();
            filter.doFilter(request, lastResponse, chain);
        }

        assertThat(lastResponse.getStatus()).isEqualTo(429);
    }

    @Test
    void resolveClientIp_ignoresForwardedHeader_whenNotTrusted() throws Exception {
        var filter = filterWith(false, counterStore());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.setRemoteAddr("203.0.113.40");
            request.addHeader("X-Forwarded-For", "1.2.3.4");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        var legit = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        legit.setServletPath("/api/v1/auth/login");
        legit.setRemoteAddr("203.0.113.40");
        legit.addHeader("X-Forwarded-For", "5.6.7.8");
        var response = new MockHttpServletResponse();
        filter.doFilter(legit, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void resolveClientIp_usesLastHopOfForwardedHeader_whenTrusted() throws Exception {
        var filter = filterWith(true, counterStore());
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.setRemoteAddr("198.51.100.1");
            request.addHeader("X-Forwarded-For", "203.0.113." + i + ", 10.0.0.5");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        var blocked = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blocked.setServletPath("/api/v1/auth/login");
        blocked.setRemoteAddr("198.51.100.2");
        blocked.addHeader("X-Forwarded-For", "203.0.113.99, 10.0.0.5");
        var response = new MockHttpServletResponse();

        filter.doFilter(blocked, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }
}
