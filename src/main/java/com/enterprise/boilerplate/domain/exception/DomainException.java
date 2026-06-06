package com.enterprise.boilerplate.domain.exception;

public sealed class DomainException extends RuntimeException
        permits InvalidEmailException,
                UserAlreadyExistsException,
                InvalidPasswordException,
                UserNotFoundException,
                InvalidTokenException,
                InactiveUserException,
                ForbiddenException {

    protected DomainException(String message) {
        super(message);
    }
}
