package com.fooddelivery.delivery.api.dto;

import java.util.UUID;

/**
 * Request DTO cho lập lịch giao vận.
 */
public record DeliveryRequest(
        UUID orderId,
        String deliveryAddressSnapshot // JSON string hoặc plain text address
) {}
