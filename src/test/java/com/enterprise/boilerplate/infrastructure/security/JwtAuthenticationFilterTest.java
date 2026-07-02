package com.enterprise.boilerplate.infrastructure.security;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.repository.UserRepository;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenService jwtTokenService;
    @Mock private UserRepository userRepository;
    @Mock private FilterChain chain;

    private JwtAuthenticationFilter filter;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final PasswordHash HASH =
            PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenService, userRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNotSetAuthentication_whenNoAuthorizationHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotSetAuthentication_whenHeaderIsNotBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotSetAuthentication_whenTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.parseAccessToken("invalid-token")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void setsAuthentication_whenTokenIsValidAndUserIsActive() throws Exception {
        User activeUser = User.create(Email.of("a@b.com"), HASH, "Alice", User.Role.USER);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.parseAccessToken("valid-token"))
                .thenReturn(Optional.of(new TokenClaims(USER_ID, "USER")));
        when(userRepository.findById(UserId.of(USER_ID))).thenReturn(Optional.of(activeUser));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(USER_ID);
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotSetAuthentication_whenUserNotFound() throws Exception {
        String ghostId = UUID.randomUUID().toString();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ghost-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.parseAccessToken("ghost-token"))
                .thenReturn(Optional.of(new TokenClaims(ghostId, "USER")));
        when(userRepository.findById(UserId.of(ghostId))).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotSetAuthentication_whenUserIsInactive() throws Exception {
        String inactiveId = UUID.randomUUID().toString();
        User inactiveUser = User.create(Email.of("a@b.com"), HASH, "Alice", User.Role.USER);
        inactiveUser.deactivate();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer inactive-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.parseAccessToken("inactive-token"))
                .thenReturn(Optional.of(new TokenClaims(inactiveId, "USER")));
        when(userRepository.findById(UserId.of(inactiveId))).thenReturn(Optional.of(inactiveUser));

        filter.doFilterInternal(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void setsRoleAuthority_matchingTokenClaims() throws Exception {
        String adminId = UUID.randomUUID().toString();
        User adminUser = User.create(Email.of("admin@b.com"), HASH, "Admin", User.Role.ADMIN);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer admin-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenService.parseAccessToken("admin-token"))
                .thenReturn(Optional.of(new TokenClaims(adminId, "ADMIN")));
        when(userRepository.findById(any())).thenReturn(Optional.of(adminUser));

        filter.doFilterInternal(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
