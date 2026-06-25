package com.fooddelivery.customer.application.command;

import com.fooddelivery.customer.domain.model.enums.UserRole;

import java.util.UUID;

public record ChangeUserRoleCommand(
    UUID userId,
    UserRole newRole,
    UUID currentUserId
) {}
