package com.fooddelivery.restaurant.infrastructure.client.dto;

import java.util.UUID;

public record ReviewEligibilityResponse(
        UUID orderId,
        boolean eligible,
        String reason
) {}
