package com.fooddelivery.customer.api.dto.request;

import com.fooddelivery.customer.domain.model.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
    @NotNull UserRole role
) {}
