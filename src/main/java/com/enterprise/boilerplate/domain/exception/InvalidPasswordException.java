package com.enterprise.boilerplate.domain.exception;

public final class InvalidPasswordException extends DomainException {
    public InvalidPasswordException() {
        super("Password does not meet security requirements");
    }
}
