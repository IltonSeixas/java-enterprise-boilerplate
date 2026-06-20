package com.enterprise.boilerplate.application.dto;

public record ListUsersRequest(String role, Boolean active, String nameContains, int page, int size) {
}
