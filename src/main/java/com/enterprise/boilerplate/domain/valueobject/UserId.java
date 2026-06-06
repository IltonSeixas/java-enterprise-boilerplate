package com.enterprise.boilerplate.domain.valueobject;

import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId value must not be null");
        }
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String id) {
        try {
            return new UserId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UserId format: " + id, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
