package com.enterprise.boilerplate.domain.entity;

import com.enterprise.boilerplate.domain.valueobject.Email;
import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Email EMAIL = Email.of("user@example.com");
    private static final PasswordHash HASH = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA");

    @Test
    void create_withValidData_setsActiveAndTimestamps() {
        User user = User.create(EMAIL, HASH, "Alice", User.Role.USER);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getName()).isEqualTo("Alice");
        assertThat(user.getRole()).isEqualTo(User.Role.USER);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_withBlankName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> User.create(EMAIL, HASH, "  ", User.Role.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name must not be blank");
    }

    @Test
    void updateProfile_withValidName_updatesName() {
        User user = User.create(EMAIL, HASH, "Alice", User.Role.USER);
        Instant before = user.getUpdatedAt();

        user.updateProfile("Bob");

        assertThat(user.getName()).isEqualTo("Bob");
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void updateProfile_withBlankName_throwsIllegalArgumentException() {
        User user = User.create(EMAIL, HASH, "Alice", User.Role.USER);

        assertThatThrownBy(() -> user.updateProfile(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name must not be blank");
    }

    @Test
    void changePassword_updatesHashAndUpdatedAt() throws InterruptedException {
        User user = User.create(EMAIL, HASH, "Alice", User.Role.USER);
        Instant before = user.getUpdatedAt();
        Thread.sleep(1);

        PasswordHash newHash = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$bmV3SGFzaA");
        user.changePassword(newHash);

        assertThat(user.getPasswordHash()).isEqualTo(newHash);
        assertThat(user.getUpdatedAt()).isAfter(before);
    }
}
