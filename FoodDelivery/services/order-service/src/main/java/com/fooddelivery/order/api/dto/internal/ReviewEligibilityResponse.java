package com.fooddelivery.order.api.dto.internal;

import java.util.UUID;

public record ReviewEligibilityResponse(
        UUID orderId,
        boolean eligible,
        String reason
) {}
