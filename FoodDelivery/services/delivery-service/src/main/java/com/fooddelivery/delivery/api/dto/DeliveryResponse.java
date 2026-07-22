package com.fooddelivery.delivery.api.dto;

import java.util.UUID;

/**
 * Response DTO for internal delivery schedule / lookup.
 */
public record DeliveryResponse(
        UUID deliveryId,
        UUID orderId,
        String status,
        UUID driverId,
        String message
) {
}
