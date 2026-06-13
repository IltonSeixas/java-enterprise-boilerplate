package com.enterprise.boilerplate.domain.exception;

public sealed class DomainException extends RuntimeException
        permits InvalidEmailException,
                UserAlreadyExistsException,
                InvalidPasswordException,
                InvalidNameException,
                InvalidUserIdException,
                UserNotFoundException,
                InvalidTokenException,
                InactiveUserException,
                ForbiddenException,
                InsufficientPermissionsException,
                InvalidRoleException {

    protected DomainException(String message) {
        super(message);
    }
}
