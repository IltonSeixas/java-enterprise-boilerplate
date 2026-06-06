package com.enterprise.boilerplate.domain.exception;

public final class ForbiddenException extends DomainException {
    public ForbiddenException() {
        super("Access denied");
    }
}
