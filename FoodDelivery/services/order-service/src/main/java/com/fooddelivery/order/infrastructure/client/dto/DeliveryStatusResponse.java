package com.fooddelivery.order.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Remote delivery truth used by order-side reconciliation.
 * Timestamps come from delivery-service when available; never invent scheduler "now".
 */
public record DeliveryStatusResponse(
        UUID deliveryId,
        UUID orderId,
        String status,
        UUID driverId,
        String message,
        Instant pickedUpAt,
        Instant deliveryStartedAt,
        Instant deliveredAt,
        Instant failedAt,
        String failureReason
) {
    public static DeliveryStatusResponse from(DeliveryResponse response) {
        if (response == null) {
            return null;
        }
        return new DeliveryStatusResponse(
                response.deliveryId(),
                response.orderId(),
                response.status(),
                response.driverId(),
                response.message(),
                null,
                null,
                null,
                null,
                null);
    }

    public boolean isTerminalFailure() {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return "FAILED".equals(normalized) || "CANCELLED".equals(normalized);
    }
}
