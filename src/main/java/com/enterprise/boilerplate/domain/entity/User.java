package com.enterprise.boilerplate.domain.entity;

import com.enterprise.boilerplate.domain.exception.InsufficientPermissionsException;
import com.enterprise.boilerplate.domain.exception.InvalidNameException;
import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import com.enterprise.boilerplate.domain.valueobject.UserId;

import java.time.Instant;

public final class User {

    public enum Role { USER, ADMIN, OWNER }

    private final UserId id;
    private Email email;
    private PasswordHash passwordHash;
    private String name;
    private Role role;
    private boolean active;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Email email, PasswordHash passwordHash, String name,
                 Role role, boolean active, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User create(Email email, PasswordHash passwordHash, String name, Role role) {
        if (name == null || name.isBlank()) {
            throw new InvalidNameException();
        }
        Instant now = Instant.now();
        return new User(UserId.generate(), email, passwordHash, name.trim(), role, true, now, now);
    }

    public static User reconstitute(UserId id, Email email, PasswordHash passwordHash,
                                    String name, Role role, boolean active,
                                    Instant createdAt, Instant updatedAt) {
        return new User(id, email, passwordHash, name, role, active, createdAt, updatedAt);
    }

    public void updateProfile(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new InvalidNameException();
        }
        this.name = newName.trim();
        this.updatedAt = Instant.now();
    }

    public void changePassword(PasswordHash newHash) {
        this.passwordHash = newHash;
        this.updatedAt = Instant.now();
    }

    public boolean canChangeRoleOf(User target) {
        return this.role == Role.OWNER && !this.id.equals(target.id);
    }

    public void changeRole(Role newRole, User actor) {
        if (!actor.canChangeRoleOf(this)) {
            throw new InsufficientPermissionsException();
        }
        this.role = newRole;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public UserId getId() { return id; }
    public Email getEmail() { return email; }
    public PasswordHash getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
