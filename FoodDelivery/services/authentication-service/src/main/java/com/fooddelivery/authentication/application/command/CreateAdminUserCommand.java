package com.fooddelivery.authentication.application.command;

import com.fooddelivery.authentication.domain.model.enums.UserRole;

import java.util.UUID;

public record CreateAdminUserCommand(
    UUID currentUserId,
    String email,
    String phone,
    String password,
    UserRole role
) {}
