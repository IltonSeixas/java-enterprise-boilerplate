package com.enterprise.boilerplate.infrastructure.security;

import com.enterprise.boilerplate.domain.valueobject.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

    @Test
    void hash_producesArgon2idFormat() {
        PasswordHash hash = hasher.hash("secret123");

        assertThat(hash.value()).startsWith("$argon2id$");
    }

    @Test
    void hash_isDifferentEachCall_dueTo_randomSalt() {
        PasswordHash first = hasher.hash("same-password");
        PasswordHash second = hasher.hash("same-password");

        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    void verify_returnsTrueForCorrectPassword() {
        PasswordHash hash = hasher.hash("correct-horse-battery-staple");

        assertThat(hasher.verify("correct-horse-battery-staple", hash)).isTrue();
    }

    @Test
    void verify_returnsFalseForWrongPassword() {
        PasswordHash hash = hasher.hash("correct-password");

        assertThat(hasher.verify("wrong-password", hash)).isFalse();
    }

    @Test
    void verify_returnsFalseForMalformedHash() {
        PasswordHash malformed = PasswordHash.of("$argon2id$not-valid-hash");

        assertThat(hasher.verify("any-password", malformed)).isFalse();
    }

    @Test
    void verify_returnsFalseForTruncatedHashWithWrongPartCount() {
        PasswordHash tooFewParts = PasswordHash.of("$argon2id$v=19$m=65536,t=3,p=4$only-one-part");

        assertThat(hasher.verify("any-password", tooFewParts)).isFalse();
    }

    @Test
    void hash_encodedPartsMatchConfiguredParameters() {
        PasswordHash hash = hasher.hash("test");
        String[] parts = hash.value().split("\\$");

        // parts[3] = "m=65536,t=3,p=4"
        assertThat(parts[3]).contains("m=65536").contains("t=3").contains("p=4");
    }
}
