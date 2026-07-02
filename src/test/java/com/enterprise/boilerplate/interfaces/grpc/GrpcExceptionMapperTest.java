package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InsufficientPermissionsException;
import com.enterprise.boilerplate.domain.exception.InvalidEmailException;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.InvalidRoleException;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcExceptionMapperTest {

    private static Status statusOf(Throwable t) {
        return Status.fromThrowable(GrpcExceptionMapper.toStatusException(t));
    }

    @Test
    void mapsInvalidEmailException_toInvalidArgument() {
        assertThat(statusOf(new InvalidEmailException("bad")).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void mapsInvalidPasswordException_toInvalidArgument() {
        assertThat(statusOf(new InvalidPasswordException()).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void mapsInvalidRoleException_toInvalidArgument() {
        assertThat(statusOf(new InvalidRoleException("superuser")).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void mapsConstraintViolationException_toInvalidArgument() {
        assertThat(statusOf(new ConstraintViolationException(Set.of())).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void mapsUserAlreadyExistsException_toAlreadyExists() {
        assertThat(statusOf(new UserAlreadyExistsException("a@b.com")).getCode())
                .isEqualTo(Status.Code.ALREADY_EXISTS);
    }

    @Test
    void mapsUserNotFoundException_toNotFound() {
        assertThat(statusOf(new UserNotFoundException("id")).getCode())
                .isEqualTo(Status.Code.NOT_FOUND);
    }

    @Test
    void mapsInvalidTokenException_toUnauthenticated() {
        assertThat(statusOf(new InvalidTokenException()).getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void mapsInactiveUserException_toPermissionDenied() {
        assertThat(statusOf(new InactiveUserException()).getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void mapsForbiddenException_toPermissionDenied() {
        assertThat(statusOf(new ForbiddenException()).getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void mapsInsufficientPermissionsException_toPermissionDenied() {
        assertThat(statusOf(new InsufficientPermissionsException()).getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    @Test
    void mapsUnknownException_toInternal_withGenericDescription() {
        StatusRuntimeException ex = GrpcExceptionMapper.toStatusException(new RuntimeException("boom"));

        assertThat(Status.fromThrowable(ex).getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(ex.getStatus().getDescription()).isEqualTo("An internal error occurred");
    }

    @Test
    void knownExceptions_preserveOriginalMessage() {
        StatusRuntimeException ex = GrpcExceptionMapper.toStatusException(new UserNotFoundException("abc"));

        assertThat(ex.getStatus().getDescription()).contains("abc");
    }
}
