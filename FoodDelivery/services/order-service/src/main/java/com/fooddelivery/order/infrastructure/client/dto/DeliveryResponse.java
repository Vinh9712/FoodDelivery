package com.fooddelivery.order.infrastructure.client.dto;

import java.util.UUID;

/** Response received from Delivery Service schedule / lookup. */
public record DeliveryResponse(
        UUID deliveryId,
        UUID orderId,
        String status,
        UUID driverId,
        String message
) {
    /** True when a driver was assigned on this call or already assigned. */
    public boolean isAssigned() {
        return "ASSIGNED".equalsIgnoreCase(status)
                || "DRIVER_ASSIGNED".equalsIgnoreCase(status);
    }
}
