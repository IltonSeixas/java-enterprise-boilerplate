package com.enterprise.boilerplate.interfaces.grpc;

import com.enterprise.boilerplate.domain.exception.ForbiddenException;
import com.enterprise.boilerplate.domain.exception.InactiveUserException;
import com.enterprise.boilerplate.domain.exception.InvalidEmailException;
import com.enterprise.boilerplate.domain.exception.InvalidNameException;
import com.enterprise.boilerplate.domain.exception.InvalidPasswordException;
import com.enterprise.boilerplate.domain.exception.InvalidTokenException;
import com.enterprise.boilerplate.domain.exception.InvalidUserIdException;
import com.enterprise.boilerplate.domain.exception.UserAlreadyExistsException;
import com.enterprise.boilerplate.domain.exception.UserNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;

/**
 * Translates domain and validation errors into {@link StatusRuntimeException}s with
 * the gRPC status code that best matches their REST counterpart in {@code GlobalExceptionHandler}.
 */
public final class GrpcExceptionMapper {

    private GrpcExceptionMapper() {
    }

    public static StatusRuntimeException toStatusException(Throwable error) {
        return statusFor(error).withDescription(error.getMessage()).withCause(error).asRuntimeException();
    }

    private static Status statusFor(Throwable error) {
        return switch (error) {
            case InvalidEmailException e -> Status.INVALID_ARGUMENT;
            case InvalidPasswordException e -> Status.INVALID_ARGUMENT;
            case InvalidNameException e -> Status.INVALID_ARGUMENT;
            case InvalidUserIdException e -> Status.INVALID_ARGUMENT;
            case ConstraintViolationException e -> Status.INVALID_ARGUMENT;
            case UserAlreadyExistsException e -> Status.ALREADY_EXISTS;
            case UserNotFoundException e -> Status.NOT_FOUND;
            case InvalidTokenException e -> Status.UNAUTHENTICATED;
            case InactiveUserException e -> Status.PERMISSION_DENIED;
            case ForbiddenException e -> Status.PERMISSION_DENIED;
            default -> Status.INTERNAL;
        };
    }
}
