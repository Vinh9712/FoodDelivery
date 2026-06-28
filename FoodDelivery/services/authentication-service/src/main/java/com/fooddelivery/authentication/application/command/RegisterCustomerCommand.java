package com.fooddelivery.authentication.application.command;

import com.fooddelivery.authentication.domain.model.enums.UserRole;

public record RegisterCustomerCommand(
    String email,
    String phone,
    String password,
    String fullName,
    UserRole role
) {}
