package com.enterprise.boilerplate.domain.repository;

import com.enterprise.boilerplate.domain.entity.User;

import java.util.List;

public record UserPage(List<User> content, long totalElements) {
}
