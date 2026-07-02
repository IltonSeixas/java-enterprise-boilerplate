package com.enterprise.boilerplate.interfaces.rest;

import com.enterprise.boilerplate.application.dto.PageResponse;
import com.enterprise.boilerplate.application.dto.UserResponse;
import com.enterprise.boilerplate.application.usecase.ChangePasswordUseCase;
import com.enterprise.boilerplate.application.usecase.ChangeUserRoleUseCase;
import com.enterprise.boilerplate.application.usecase.GetUserUseCase;
import com.enterprise.boilerplate.application.usecase.ListUsersUseCase;
import com.enterprise.boilerplate.application.usecase.UpdateProfileUseCase;
import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private GetUserUseCase getUserUseCase;
    @Mock private UpdateProfileUseCase updateProfileUseCase;
    @Mock private ChangePasswordUseCase changePasswordUseCase;
    @Mock private ChangeUserRoleUseCase changeUserRoleUseCase;
    @Mock private ListUsersUseCase listUsersUseCase;

    private MockMvc mockMvc;
    private final ObjectMapper json = new ObjectMapper();

    private static final String CALLER_ID = "caller-uuid";
    private static final Authentication CALLER = new TestingAuthenticationToken(CALLER_ID, null);

    private static final UserResponse ALICE = new UserResponse(
            "alice-id", "alice@example.com", "Alice", "USER", true, Instant.parse("2026-01-01T00:00:00Z"));

    @BeforeEach
    void setUp() {
        var controller = new UserController(
                getUserUseCase, updateProfileUseCase, changePasswordUseCase,
                changeUserRoleUseCase, listUsersUseCase);

        var mvpp = new MethodValidationPostProcessor();
        mvpp.afterPropertiesSet();
        var proxied = mvpp.postProcessAfterInitialization(controller, "userController");

        mockMvc = MockMvcBuilders.standaloneSetup(proxied)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockHttpServletRequestBuilder authedGet(String url) {
        return get(url).principal(CALLER);
    }

    private MockHttpServletRequestBuilder authedPut(String url) {
        return put(url).principal(CALLER).contentType(MediaType.APPLICATION_JSON);
    }

    // --- GET /me ---

    @Test
    void getMe_returns200_withUserBody() throws Exception {
        when(getUserUseCase.execute(CALLER_ID, CALLER_ID)).thenReturn(ALICE);

        mockMvc.perform(authedGet("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void getMe_returns404_whenUserNotFound() throws Exception {
        when(getUserUseCase.execute(CALLER_ID, CALLER_ID))
                .thenThrow(new UserNotFoundException(CALLER_ID));

        mockMvc.perform(authedGet("/api/v1/users/me"))
                .andExpect(status().isNotFound());
    }

    // --- GET /{id} ---

    @Test
    void getById_returns200_withUserBody() throws Exception {
        when(getUserUseCase.execute("alice-id", CALLER_ID)).thenReturn(ALICE);

        mockMvc.perform(authedGet("/api/v1/users/alice-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("alice-id"));
    }

    @Test
    void getById_returns403_whenForbidden() throws Exception {
        when(getUserUseCase.execute("alice-id", CALLER_ID))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(authedGet("/api/v1/users/alice-id"))
                .andExpect(status().isForbidden());
    }

    // --- GET / (list) ---

    @Test
    void list_returns200_withPage() throws Exception {
        when(listUsersUseCase.execute(any(), any()))
                .thenReturn(PageResponse.of(List.of(ALICE), 0, 20, 1));

        mockMvc.perform(authedGet("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_returns403_whenCallerLacksPermission() throws Exception {
        when(listUsersUseCase.execute(any(), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(authedGet("/api/v1/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returns400_whenSortByInvalid() throws Exception {
        when(listUsersUseCase.execute(any(), any()))
                .thenThrow(new IllegalArgumentException("Invalid sortBy field: unknown"));

        mockMvc.perform(authedGet("/api/v1/users").param("sortBy", "unknown"))
                .andExpect(status().isBadRequest());
    }

    // --- PUT /me ---

    @Test
    void updateMe_returns200_withUpdatedUser() throws Exception {
        when(updateProfileUseCase.execute(eq(CALLER_ID), any())).thenReturn(ALICE);

        mockMvc.perform(authedPut("/api/v1/users/me")
                        .content(json.writeValueAsString(new NameBody("Alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void updateMe_returns400_whenNameMissing() throws Exception {
        mockMvc.perform(authedPut("/api/v1/users/me").content("{}"))
                .andExpect(status().isBadRequest());
    }

    // --- PUT /me/password ---

    @Test
    void changePassword_returns204_onSuccess() throws Exception {
        mockMvc.perform(authedPut("/api/v1/users/me/password")
                        .content(json.writeValueAsString(new PasswordBody("old-pass", "New!pass1"))))
                .andExpect(status().isNoContent());

        verify(changePasswordUseCase).execute(eq(CALLER_ID), any());
    }

    @Test
    void changePassword_returns401_whenCurrentPasswordWrong() throws Exception {
        doThrow(new InvalidPasswordException())
                .when(changePasswordUseCase).execute(eq(CALLER_ID), any());

        mockMvc.perform(authedPut("/api/v1/users/me/password")
                        .content(json.writeValueAsString(new PasswordBody("wrong", "New!pass1"))))
                .andExpect(status().isUnauthorized());
    }

    // --- PUT /{id}/role ---

    @Test
    void changeRole_returns200_withUpdatedUser() throws Exception {
        when(changeUserRoleUseCase.execute(eq(CALLER_ID), eq("alice-id"), any()))
                .thenReturn(ALICE);

        mockMvc.perform(authedPut("/api/v1/users/alice-id/role")
                        .content(json.writeValueAsString(new RoleBody("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void changeRole_returns403_whenInsufficientPermissions() throws Exception {
        when(changeUserRoleUseCase.execute(eq(CALLER_ID), eq("alice-id"), any()))
                .thenThrow(new ForbiddenException());

        mockMvc.perform(authedPut("/api/v1/users/alice-id/role")
                        .content(json.writeValueAsString(new RoleBody("ADMIN"))))
                .andExpect(status().isForbidden());
    }

    private record NameBody(String name) {}
    private record PasswordBody(String currentPassword, String newPassword) {}
    private record RoleBody(String role) {}
}
