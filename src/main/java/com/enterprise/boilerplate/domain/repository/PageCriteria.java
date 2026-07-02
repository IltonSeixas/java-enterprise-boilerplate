package com.enterprise.boilerplate.domain.repository;

public record PageCriteria(int page, int size, String sortBy, SortDirection direction) {

    public enum SortDirection { ASC, DESC }

    public PageCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
        if (sortBy == null || sortBy.isBlank()) {
            throw new IllegalArgumentException("sortBy must not be blank");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
    }

    public static PageCriteria of(int page, int size) {
        return new PageCriteria(page, size, "createdAt", SortDirection.ASC);
    }

    public static PageCriteria of(int page, int size, String sortBy, SortDirection direction) {
        return new PageCriteria(page, size, sortBy, direction);
    }
}
