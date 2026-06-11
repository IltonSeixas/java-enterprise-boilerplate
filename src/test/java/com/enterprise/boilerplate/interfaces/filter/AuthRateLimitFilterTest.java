package com.enterprise.boilerplate.interfaces.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthRateLimitFilterTest {

    @Test
    void shouldNotFilter_returnsTrue_forNonAuthPaths() {
        var filter = new AuthRateLimitFilter(false);
        var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.setServletPath("/api/v1/users/me");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_returnsFalse_forAuthPaths() {
        var filter = new AuthRateLimitFilter(false);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setServletPath("/api/v1/auth/login");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldNotFilter_usesNormalizedServletPath_notRawUri() {
        var filter = new AuthRateLimitFilter(false);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login;jsessionid=abc");
        request.setServletPath("/api/v1/auth/login");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilterInternal_allowsRequestsUnderLimit() throws Exception {
        var filter = new AuthRateLimitFilter(false);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.setRemoteAddr("203.0.113.10");
            var response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
        }

        verify(chain, times(10)).doFilter(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doFilterInternal_blocksRequestsOverLimit() throws Exception {
        var filter = new AuthRateLimitFilter(false);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < 11; i++) {
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
    void resolveClientIp_ignoresForwardedHeader_whenNotTrusted() throws Exception {
        var filter = new AuthRateLimitFilter(false);
        FilterChain chain = mock(FilterChain.class);

        var spoofed = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        spoofed.setServletPath("/api/v1/auth/login");
        spoofed.setRemoteAddr("203.0.113.30");
        spoofed.addHeader("X-Forwarded-For", "1.2.3.4");

        var legit = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        legit.setServletPath("/api/v1/auth/login");
        legit.setRemoteAddr("203.0.113.30");
        legit.addHeader("X-Forwarded-For", "5.6.7.8");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(spoofed, new MockHttpServletResponse(), chain);
        }

        var response = new MockHttpServletResponse();
        filter.doFilter(legit, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void resolveClientIp_usesForwardedHeader_whenTrusted() throws Exception {
        var filter = new AuthRateLimitFilter(true);
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.setRemoteAddr("198.51.100.1");
            request.addHeader("X-Forwarded-For", "9.9.9.9");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
        }

        var blocked = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blocked.setServletPath("/api/v1/auth/login");
        blocked.setRemoteAddr("198.51.100.2");
        blocked.addHeader("X-Forwarded-For", "9.9.9.9");
        var response = new MockHttpServletResponse();

        filter.doFilter(blocked, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }
}
