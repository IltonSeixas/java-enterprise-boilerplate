package com.enterprise.boilerplate.domain.exception;

public final class InactiveUserException extends DomainException {
    public InactiveUserException() {
        super("User account is deactivated");
    }
}
