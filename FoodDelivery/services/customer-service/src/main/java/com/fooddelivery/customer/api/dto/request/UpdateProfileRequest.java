package com.fooddelivery.customer.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must be at most 150 characters")
    String fullName,

    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Phone must be valid (9-15 digits)")
    String phone,

    String avatarUrl
) {}
