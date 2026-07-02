package com.enterprise.boilerplate.interfaces.rest;

import com.enterprise.boilerplate.application.dto.AuthResponse;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.application.usecase.LoginUserUseCase;
import com.enterprise.boilerplate.application.usecase.LogoutUseCase;
import com.enterprise.boilerplate.application.usecase.RefreshTokenUseCase;
import com.enterprise.boilerplate.application.usecase.RegisterUserUseCase;
import com.enterprise.boilerplate.config.properties.JwtProperties;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private RegisterUserUseCase registerUserUseCase;
    @Mock
    private LoginUserUseCase loginUserUseCase;
    @Mock
    private RefreshTokenUseCase refreshTokenUseCase;
    @Mock
    private LogoutUseCase logoutUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    private static final UserResponse ALICE = new UserResponse(
            "user-id", "alice@example.com", "Alice", "USER", true, Instant.now());

    private static final AuthResponse AUTH = AuthResponse.of("access-token", "refresh-token", 900);

    @BeforeEach
    void setUp() {
        var jwtProperties = new JwtProperties("private.pem", "public.pem", 15, 7);
        var controller = new AuthController(
                registerUserUseCase, loginUserUseCase, refreshTokenUseCase, logoutUseCase, jwtProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- register ---

    @Test
    void register_returns201_withUserBody() throws Exception {
        when(registerUserUseCase.execute(any())).thenReturn(ALICE);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterBody("alice@example.com", "S3cure!pass", "Alice"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void register_returns409_whenEmailAlreadyExists() throws Exception {
        when(registerUserUseCase.execute(any()))
                .thenThrow(new UserAlreadyExistsException("alice@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RegisterBody("alice@example.com", "S3cure!pass", "Alice"))))
                .andExpect(status().isConflict());
    }

    @Test
    void register_returns400_whenBodyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- login ---

    @Test
    void login_returns200_withAccessToken_andSetsCookie() throws Exception {
        when(loginUserUseCase.execute(any())).thenReturn(AUTH);

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody("alice@example.com", "S3cure!pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("refresh_token=refresh-token")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }

    @Test
    void login_returns401_whenCredentialsInvalid() throws Exception {
        when(loginUserUseCase.execute(any()))
                .thenThrow(new InvalidPasswordException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody("alice@example.com", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns404_whenUserNotFound() throws Exception {
        when(loginUserUseCase.execute(any()))
                .thenThrow(new UserNotFoundException("alice@example.com"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LoginBody("alice@example.com", "pass"))))
                .andExpect(status().isNotFound());
    }

    // --- refresh ---

    @Test
    void refresh_returns200_whenCookiePresent() throws Exception {
        when(refreshTokenUseCase.execute(any())).thenReturn(AUTH);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refresh_returns401_whenCookieMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_returns401_whenTokenInvalid() throws Exception {
        when(refreshTokenUseCase.execute(any()))
                .thenThrow(new InvalidTokenException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "bad-token")))
                .andExpect(status().isUnauthorized());
    }

    // --- logout ---

    @Test
    void logout_returns204_andClearsCookie() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "some-token")))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(logoutUseCase).execute("some-token");

        String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie).contains("refresh_token=").contains("Max-Age=0");
    }

    @Test
    void logout_returns204_evenWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());
    }

    // helper records to build JSON bodies without depending on application DTOs
    private record RegisterBody(String email, String password, String name) {}
    private record LoginBody(String email, String password) {}
}
