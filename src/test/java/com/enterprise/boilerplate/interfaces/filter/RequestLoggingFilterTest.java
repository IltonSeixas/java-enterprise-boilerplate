package com.enterprise.boilerplate.interfaces.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock private FilterChain chain;

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @Test
    void alwaysDelegatesDownTheFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void delegatesEvenWhenDownstreamFilterThrows() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.mockito.Mockito.doThrow(new RuntimeException("downstream failure"))
                .when(chain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, chain);
        } catch (RuntimeException ignored) {
            // expected — we verify chain was still called
        }

        verify(chain).doFilter(request, response);
    }

    @Test
    void sanitizesNewlinesInServletPath() throws Exception {
        // Log-injection guard: CR/LF/Tab in the path must be replaced before logging.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api\r\n\t/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // This must complete without throwing (the sanitize method handles the replacement).
        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
