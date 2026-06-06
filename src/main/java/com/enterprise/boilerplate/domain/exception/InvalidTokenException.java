package com.enterprise.boilerplate.domain.exception;

public final class InvalidTokenException extends DomainException {
    public InvalidTokenException() {
        super("Token is invalid or expired");
    }
}
