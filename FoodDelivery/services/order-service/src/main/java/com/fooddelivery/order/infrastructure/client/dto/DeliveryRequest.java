package com.fooddelivery.order.infrastructure.client.dto;

import java.util.UUID;

/** Request gửi đến Delivery Service để lập lịch giao vận. */
public record DeliveryRequest(
        UUID orderId,
        UUID customerId,
        String deliveryAddressSnapshot
) {
    public DeliveryRequest(UUID orderId, String deliveryAddressSnapshot) {
        this(orderId, null, deliveryAddressSnapshot);
    }
}
