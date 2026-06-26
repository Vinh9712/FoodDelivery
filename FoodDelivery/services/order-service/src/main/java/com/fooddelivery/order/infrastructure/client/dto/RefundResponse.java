package com.fooddelivery.order.infrastructure.client.dto;

import java.util.UUID;

/** Response nhận từ Payment Service sau hoàn tiền. */
public record RefundResponse(
        UUID orderId,
        String status,  // "REFUNDED"
        String message
) {}
