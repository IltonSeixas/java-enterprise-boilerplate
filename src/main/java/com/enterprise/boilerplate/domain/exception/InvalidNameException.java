package com.enterprise.boilerplate.domain.exception;

public final class InvalidNameException extends DomainException {
    public InvalidNameException() {
        super("Name must not be blank");
    }
}
