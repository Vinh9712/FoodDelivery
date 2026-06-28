package com.fooddelivery.authentication.application.command;

import com.fooddelivery.authentication.domain.model.enums.UserRole;

public record ListUsersQuery(
    int page,
    int size,
    String search,
    UserRole role,
    Boolean active
) {}
