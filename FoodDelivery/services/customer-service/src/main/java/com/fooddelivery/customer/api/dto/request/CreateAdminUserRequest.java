package com.fooddelivery.customer.api.dto.request;

import com.fooddelivery.customer.domain.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAdminUserRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 10, max = 20) String phone,
    @NotBlank @Size(min = 6) String password,
    @NotNull UserRole role
) {}
