package com.fooddelivery.authentication.application.command;

import com.fooddelivery.authentication.domain.model.enums.UserRole;

import java.util.UUID;

public record ChangeUserRoleCommand(
    UUID userId,
    UserRole newRole,
    UUID currentUserId
) {}
