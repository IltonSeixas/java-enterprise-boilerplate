package com.enterprise.boilerplate.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeRoleRequest(@NotBlank String role) {}
