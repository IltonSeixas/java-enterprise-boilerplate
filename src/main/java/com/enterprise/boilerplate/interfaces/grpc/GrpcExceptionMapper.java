package com.enterprise.boilerplate.interfaces.grpc;

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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GrpcExceptionMapper {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionMapper.class);

    private GrpcExceptionMapper() {
    }

    public static StatusRuntimeException toStatusException(Throwable error) {
        Status status = statusFor(error);
        if (status == Status.INTERNAL) {
            log.error("Unhandled gRPC error", error);
            return status.withDescription("An internal error occurred").asRuntimeException();
        }
        return status.withDescription(error.getMessage()).withCause(error).asRuntimeException();
    }

    private static Status statusFor(Throwable error) {
        return switch (error) {
            case InvalidEmailException e -> Status.INVALID_ARGUMENT;
            case InvalidPasswordException e -> Status.INVALID_ARGUMENT;
            case InvalidNameException e -> Status.INVALID_ARGUMENT;
            case InvalidUserIdException e -> Status.INVALID_ARGUMENT;
            case InvalidRoleException e -> Status.INVALID_ARGUMENT;
            case DomainValidationException e -> Status.INVALID_ARGUMENT;
            case IllegalArgumentException e -> Status.INVALID_ARGUMENT;
            case ConstraintViolationException e -> Status.INVALID_ARGUMENT;
            case UserAlreadyExistsException e -> Status.ALREADY_EXISTS;
            case UserNotFoundException e -> Status.NOT_FOUND;
            case InvalidTokenException e -> Status.UNAUTHENTICATED;
            case InactiveUserException e -> Status.PERMISSION_DENIED;
            case ForbiddenException e -> Status.PERMISSION_DENIED;
            case InsufficientPermissionsException e -> Status.PERMISSION_DENIED;
            default -> Status.INTERNAL;
        };
    }
}
