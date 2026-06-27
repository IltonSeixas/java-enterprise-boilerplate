package com.enterprise.boilerplate.interfaces.rest;

import com.enterprise.boilerplate.application.dto.PageResponse;
import com.enterprise.boilerplate.application.usecase.ChangePasswordUseCase;
import com.enterprise.boilerplate.application.usecase.ChangeUserRoleUseCase;
import com.enterprise.boilerplate.application.usecase.GetUserUseCase;
import com.enterprise.boilerplate.application.usecase.ListUsersUseCase;
import com.enterprise.boilerplate.application.usecase.UpdateProfileUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerValidationTest {

    @Mock
    private GetUserUseCase getUserUseCase;

    @Mock
    private UpdateProfileUseCase updateProfileUseCase;

    @Mock
    private ChangePasswordUseCase changePasswordUseCase;

    @Mock
    private ChangeUserRoleUseCase changeUserRoleUseCase;

    @Mock
    private ListUsersUseCase listUsersUseCase;

    private MockMvc mockMvc;
    private static final Authentication CALLER = new TestingAuthenticationToken("caller-id", null);

    @BeforeEach
    void setUp() {
        var controller = new UserController(
                getUserUseCase, updateProfileUseCase, changePasswordUseCase, changeUserRoleUseCase, listUsersUseCase);

        var methodValidationPostProcessor = new MethodValidationPostProcessor();
        methodValidationPostProcessor.afterPropertiesSet();
        var proxiedController = methodValidationPostProcessor.postProcessAfterInitialization(controller, "userController");

        mockMvc = MockMvcBuilders.standaloneSetup(proxiedController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder authenticatedGet(String url) {
        return get(url).principal(CALLER);
    }

    @Test
    void list_rejectsNegativePage() throws Exception {
        mockMvc.perform(authenticatedGet("/api/v1/users").param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_rejectsZeroSize() throws Exception {
        mockMvc.perform(authenticatedGet("/api/v1/users").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_rejectsSizeAboveMaximum() throws Exception {
        mockMvc.perform(authenticatedGet("/api/v1/users").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_acceptsValidPageAndSize() throws Exception {
        when(listUsersUseCase.execute(any(), any()))
                .thenReturn(PageResponse.of(java.util.List.of(), 0, 100, 0));

        mockMvc.perform(authenticatedGet("/api/v1/users").param("page", "0").param("size", "100"))
                .andExpect(status().isOk());
    }
}
