package com.enterprise.boilerplate.domain.repository;

import com.enterprise.boilerplate.domain.entity.User;

public record UserFilter(User.Role role, Boolean active, String nameContains) {

    public static UserFilter all() {
        return new UserFilter(null, null, null);
    }
}
