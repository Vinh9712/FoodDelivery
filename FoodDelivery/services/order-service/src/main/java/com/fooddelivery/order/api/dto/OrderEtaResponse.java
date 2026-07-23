package com.fooddelivery.order.api.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderEtaResponse(
        UUID orderId,
        int estimatedMinutes,
        Instant estimatedArrivalAt,
        String note
) {
}
