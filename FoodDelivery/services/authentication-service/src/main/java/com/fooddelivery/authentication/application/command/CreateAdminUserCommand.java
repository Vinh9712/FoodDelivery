package com.fooddelivery.authentication.application.command;

import com.fooddelivery.authentication.domain.model.enums.UserRole;

public record CreateAdminUserCommand(
    String email,
    String phone,
    String password,
    UserRole role
) {}
