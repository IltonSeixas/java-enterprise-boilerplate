package com.enterprise.boilerplate.domain.valueobject;

import com.enterprise.boilerplate.domain.exception.InvalidEmailException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @Test
    void of_withValidEmail_createsEmail() {
        Email email = Email.of("user@example.com");

        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    void of_normalizesToLowercase() {
        Email email = Email.of("User@EXAMPLE.COM");

        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    void of_withoutAtSign_throwsInvalidEmailException() {
        assertThatThrownBy(() -> Email.of("notanemail"))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void of_withNull_throwsInvalidEmailException() {
        assertThatThrownBy(() -> Email.of(null))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void equals_withSameAddress_returnsTrue() {
        Email e1 = Email.of("user@example.com");
        Email e2 = Email.of("USER@example.com");

        assertThat(e1).isEqualTo(e2);
    }

    @Test
    void equals_withDifferentAddress_returnsFalse() {
        Email e1 = Email.of("a@example.com");
        Email e2 = Email.of("b@example.com");

        assertThat(e1).isNotEqualTo(e2);
    }
}
