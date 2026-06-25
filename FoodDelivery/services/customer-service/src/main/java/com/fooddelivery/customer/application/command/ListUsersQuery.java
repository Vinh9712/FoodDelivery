package com.fooddelivery.customer.application.command;

import com.fooddelivery.customer.domain.model.enums.UserRole;

public record ListUsersQuery(
    int page,
    int size,
    String search,
    UserRole role,
    Boolean active
) {}
