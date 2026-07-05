package com.enterprise.boilerplate.application.dto;

import com.enterprise.boilerplate.domain.entity.User;

import java.time.Instant;

public record UserResponse(
        String id,
        String email,
        String name,
        String role,
        boolean active,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.id().toString(),
                user.email().value(),
                user.name(),
                user.role().name(),
                user.active(),
                user.createdAt()
        );
    }
}
