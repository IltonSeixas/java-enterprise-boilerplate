package com.enterprise.boilerplate.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeRoleRequest(
        @NotBlank
        @Pattern(regexp = "USER|ADMIN|OWNER", message = "must be one of: USER, ADMIN, OWNER")
        String role) {}
