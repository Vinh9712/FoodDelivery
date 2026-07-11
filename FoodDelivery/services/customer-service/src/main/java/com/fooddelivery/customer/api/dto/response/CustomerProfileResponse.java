package com.fooddelivery.customer.api.dto.response;

import java.util.UUID;

public record CustomerProfileResponse(
    UUID id,
    UUID authUserId,
    String email,
    String phone,
    String fullName,
    String avatarUrl,
    String customerType,
    int loyaltyPoints
) {}
