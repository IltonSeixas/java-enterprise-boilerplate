package com.enterprise.boilerplate.domain.valueobject;

import com.enterprise.boilerplate.domain.exception.DomainValidationException;

public record PasswordHash(String value) {

    public PasswordHash {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("PasswordHash must not be blank");
        }
    }

    public static PasswordHash of(String hash) {
        return new PasswordHash(hash);
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
