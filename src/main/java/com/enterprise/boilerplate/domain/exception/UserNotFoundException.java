package com.enterprise.boilerplate.domain.exception;

public final class UserNotFoundException extends DomainException {
    public UserNotFoundException(String identifier) {
        super("User not found: " + identifier);
    }
}
