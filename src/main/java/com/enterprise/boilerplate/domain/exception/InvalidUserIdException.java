package com.enterprise.boilerplate.domain.exception;

public final class InvalidUserIdException extends DomainException {
    public InvalidUserIdException(String value) {
        super("Invalid UserId format: " + value);
    }
}
