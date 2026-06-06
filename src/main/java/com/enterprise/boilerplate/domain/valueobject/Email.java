package com.enterprise.boilerplate.domain.valueobject;

import com.enterprise.boilerplate.domain.exception.InvalidEmailException;

public record Email(String value) {

    private static final String EMAIL_PATTERN =
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";

    public Email {
        if (value == null || value.isBlank() || !value.matches(EMAIL_PATTERN)) {
            throw new InvalidEmailException(value);
        }
        value = value.toLowerCase().trim();
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
