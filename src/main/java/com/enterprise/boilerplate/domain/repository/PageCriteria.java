package com.enterprise.boilerplate.domain.repository;

public record PageCriteria(int page, int size) {

    public PageCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
    }
}
