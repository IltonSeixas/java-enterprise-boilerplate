package com.enterprise.boilerplate.infrastructure.persistence.postgres;

import com.enterprise.boilerplate.domain.entity.User;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private User.Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserJpaEntity() {}

    static UserJpaEntity from(User user) {
        var entity = new UserJpaEntity();
        entity.id = user.id().value();
        entity.email = user.email().value();
        entity.passwordHash = user.passwordHash().value();
        entity.name = user.name();
        entity.role = user.role();
        entity.active = user.active();
        entity.createdAt = user.createdAt();
        entity.updatedAt = user.updatedAt();
        return entity;
    }

    User toDomain() {
        return User.reconstitute(
                new UserId(id),
                Email.of(email),
                PasswordHash.of(passwordHash),
                name,
                role,
                active,
                createdAt,
                updatedAt
        );
    }

    UUID getId() { return id; }
    String getEmail() { return email; }
    User.Role getRole() { return role; }
}
