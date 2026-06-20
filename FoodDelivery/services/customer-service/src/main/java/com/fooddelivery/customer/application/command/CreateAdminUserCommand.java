package com.fooddelivery.customer.application.command;

import com.fooddelivery.customer.domain.model.enums.UserRole;

public record CreateAdminUserCommand(
    String email,
    String phone,
    String password,
    UserRole role
) {}
