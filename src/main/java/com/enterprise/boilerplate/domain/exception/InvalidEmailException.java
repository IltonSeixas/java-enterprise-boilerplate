package com.enterprise.boilerplate.domain.exception;

public final class InvalidEmailException extends DomainException {
    public InvalidEmailException(String value) {
        super("Invalid email address: " + value);
    }
}
