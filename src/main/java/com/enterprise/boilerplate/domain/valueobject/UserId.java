package com.enterprise.boilerplate.domain.valueobject;

import com.enterprise.boilerplate.domain.exception.InvalidUserIdException;

import java.util.UUID;

public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new InvalidUserIdException("null");
        }
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String id) {
        try {
            return new UserId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new InvalidUserIdException(id);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
