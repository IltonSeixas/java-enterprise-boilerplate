package com.enterprise.boilerplate.domain.exception;

public final class InvalidRoleException extends DomainException {
    public InvalidRoleException(String value) {
        super("Invalid role: " + value);
    }
}
