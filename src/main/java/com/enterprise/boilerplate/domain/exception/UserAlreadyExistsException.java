package com.enterprise.boilerplate.domain.exception;

public final class UserAlreadyExistsException extends DomainException {
    public UserAlreadyExistsException(String email) {
        super("User already exists with email: " + email);
    }
}
