package com.enterprise.boilerplate.application.port.out;

import com.enterprise.boilerplate.domain.valueobject.PasswordHash;

public interface PasswordHasherPort {

    PasswordHash hash(String rawPassword);

    boolean verify(String rawPassword, PasswordHash hash);
}
