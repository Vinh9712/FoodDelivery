package com.fooddelivery.customer.application.command;

import com.fooddelivery.customer.domain.model.enums.UserRole;

public record RegisterCustomerCommand(
    String email,
    String phone,
    String password,
    String fullName,
    UserRole role
) {}
