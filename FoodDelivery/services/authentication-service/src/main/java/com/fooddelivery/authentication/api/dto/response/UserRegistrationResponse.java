package com.fooddelivery.authentication.api.dto.response;

import java.util.UUID;

public record UserRegistrationResponse(
        UUID userId,
        String email,
        String phone,
        String fullName,
        String role
) {}
