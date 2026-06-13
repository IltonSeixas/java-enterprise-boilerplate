package com.enterprise.boilerplate.domain.exception;

public final class InsufficientPermissionsException extends DomainException {
    public InsufficientPermissionsException() {
        super("Insufficient permissions to perform this action");
    }
}
