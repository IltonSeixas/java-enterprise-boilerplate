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
                user.getId().toString(),
                user.getEmail().value(),
                user.getName(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
