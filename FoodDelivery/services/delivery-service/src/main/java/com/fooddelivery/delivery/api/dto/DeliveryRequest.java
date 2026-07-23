package com.fooddelivery.delivery.api.dto;

import java.util.UUID;

/**
 * Request DTO for scheduling a delivery.
 */
public record DeliveryRequest(
        UUID orderId,
        UUID customerId,
        String deliveryAddressSnapshot
) {
    public DeliveryRequest(UUID orderId, String deliveryAddressSnapshot) {
        this(orderId, null, deliveryAddressSnapshot);
    }
}
