package com.fooddelivery.authentication.api.dto.request;

import com.fooddelivery.authentication.domain.model.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @NotNull UserRole role
) {}
