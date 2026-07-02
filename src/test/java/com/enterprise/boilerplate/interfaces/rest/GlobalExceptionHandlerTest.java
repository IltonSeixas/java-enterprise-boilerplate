package com.enterprise.boilerplate.interfaces.rest;

import com.enterprise.boilerplate.domain.exception.InvalidEmailException;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
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
}
