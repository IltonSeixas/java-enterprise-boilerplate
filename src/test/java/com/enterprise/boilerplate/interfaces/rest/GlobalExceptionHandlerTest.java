package com.enterprise.boilerplate.interfaces.rest;

import com.enterprise.boilerplate.domain.exception.DomainValidationException;
import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InsufficientPermissionsException;
import com.enterprise.boilerplate.domain.exception.InvalidEmailException;
import com.enterprise.boilerplate.domain.exception.InvalidNameException;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.InvalidRoleException;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.enterprise.boilerplate.domain.exception.InvalidUserIdException;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleUserNotFound_returns404() {
        ProblemDetail problem = handler.handleUserNotFound(new UserNotFoundException("id-123"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType().toString()).contains("user-not-found");
    }

    @Test
    void handleUserAlreadyExists_returns409() {
        ProblemDetail problem = handler.handleUserAlreadyExists(new UserAlreadyExistsException("a@b.com"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType().toString()).contains("user-already-exists");
    }

    @Test
    void handleForbidden_returns403() {
        ProblemDetail problem = handler.handleForbidden(new ForbiddenException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getType().toString()).contains("forbidden");
    }

    @Test
    void handleInactiveUser_returns401() {
        ProblemDetail problem = handler.handleInactiveUser(new InactiveUserException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getType().toString()).contains("inactive-user");
    }

    @Test
    void handleInvalidToken_returns401() {
        ProblemDetail problem = handler.handleInvalidToken(new InvalidTokenException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getType().toString()).contains("invalid-token");
    }

    @Test
    void handleInvalidPassword_returns401() {
        ProblemDetail problem = handler.handleInvalidPassword(new InvalidPasswordException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getType().toString()).contains("invalid-credentials");
    }

    @Test
    void handleInvalidEmail_returns400() {
        ProblemDetail problem = handler.handleInvalidEmail(new InvalidEmailException("bad@"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("invalid-email");
    }

    @Test
    void handleInvalidName_returns400() {
        ProblemDetail problem = handler.handleInvalidName(new InvalidNameException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("invalid-name");
    }

    @Test
    void handleInvalidUserId_returns400() {
        ProblemDetail problem = handler.handleInvalidUserId(new InvalidUserIdException("not-a-uuid"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("invalid-user-id");
    }

    @Test
    void handleInvalidRole_returns400() {
        ProblemDetail problem = handler.handleInvalidRole(new InvalidRoleException("superuser"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("invalid-role");
    }

    @Test
    void handleInsufficientPermissions_returns403() {
        ProblemDetail problem = handler.handleInsufficientPermissions(new InsufficientPermissionsException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getType().toString()).contains("insufficient-permissions");
    }

    @Test
    void handleDomainValidation_returns400() {
        ProblemDetail problem = handler.handleDomainValidation(new DomainValidationException("bad field"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("bad-request");
        assertThat(problem.getDetail()).isEqualTo("bad field");
    }

    @Test
    void handleIllegalArgument_returns400() {
        ProblemDetail problem = handler.handleIllegalArgument(new IllegalArgumentException("bad sortBy"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("bad-request");
        assertThat(problem.getDetail()).isEqualTo("bad sortBy");
    }

    @Test
    void handleValidation_returns400WithFieldErrors() throws NoSuchMethodException {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new org.springframework.validation.FieldError("request", "email", "must not be blank"));
        MethodParameter param = new MethodParameter(sampleMethod(), 0);
        var ex = new MethodArgumentNotValidException(param, bindingResult);

        ProblemDetail problem = handler.handleValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("validation-failed");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors).containsExactly(Map.of("field", "email", "message", "must not be blank"));
    }

    @Test
    void handleValidation_usesFallbackMessage_whenFieldErrorHasNoDefaultMessage() throws NoSuchMethodException {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new org.springframework.validation.FieldError("request", "email", null, false, null, null, null));
        MethodParameter param = new MethodParameter(sampleMethod(), 0);
        var ex = new MethodArgumentNotValidException(param, bindingResult);

        ProblemDetail problem = handler.handleValidation(ex);

        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors).containsExactly(Map.of("field", "email", "message", "invalid value"));
    }

    @Test
    void handleMethodValidation_returns400WithFieldErrors() {
        ParameterValidationResult paramResult = mock(ParameterValidationResult.class);
        MethodParameter methodParameter = mock(MethodParameter.class);
        when(methodParameter.getParameterName()).thenReturn("sortBy");
        when(paramResult.getMethodParameter()).thenReturn(methodParameter);
        var resolvableError = new org.springframework.context.support.DefaultMessageSourceResolvable(
                new String[]{"code"}, "must be one of the allowed fields");
        when(paramResult.getResolvableErrors()).thenReturn(List.of(resolvableError));

        MethodValidationResult validationResult = mock(MethodValidationResult.class);
        when(validationResult.getParameterValidationResults()).thenReturn(List.of(paramResult));
        var ex = new HandlerMethodValidationException(validationResult);

        ProblemDetail problem = handler.handleMethodValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("validation-failed");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors).containsExactly(Map.of("field", "sortBy", "message", "must be one of the allowed fields"));
    }

    @Test
    void handleConstraintViolation_returns400WithPropertyPathAndMessage() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("changePassword.newPassword");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("size must be between 8 and 100");

        var ex = new ConstraintViolationException(Set.of(violation));

        ProblemDetail problem = handler.handleConstraintViolation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType().toString()).contains("validation-failed");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors).containsExactly(
                Map.of("field", "changePassword.newPassword", "message", "size must be between 8 and 100"));
    }

    @Test
    void handleGeneric_returns500_withGenericMessage_neverLeakingExceptionDetail() {
        ProblemDetail problem = handler.handleGeneric(new RuntimeException("leaked: db password is hunter2"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getType().toString()).contains("internal-error");
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(problem.getDetail()).doesNotContain("hunter2");
    }

    private static Method sampleMethod() throws NoSuchMethodException {
        return GlobalExceptionHandlerTest.class.getDeclaredMethod("sampleTarget", String.class);
    }

    @SuppressWarnings("unused")
    private void sampleTarget(String email) {
    }
}
