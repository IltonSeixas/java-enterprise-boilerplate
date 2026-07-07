package com.enterprise.boilerplate.application.dto;

import com.enterprise.boilerplate.domain.exception.DomainValidationException;

public record PageRequest(int page, int size) {

    private static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw new DomainValidationException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new DomainValidationException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size);
    }
}
