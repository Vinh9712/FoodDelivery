package com.fooddelivery.order.infrastructure.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Response nhận từ Payment Service sau hoàn tiền. */
public record RefundResponse(
        UUID orderId,
        String status,
        String message,
        UUID paymentId,
        UUID refundId,
        BigDecimal amount,
        Instant refundedAt
) {
    /** Backward-compatible constructor used by older callers/tests. */
    public RefundResponse(UUID orderId, String status, String message) {
        this(orderId, status, message, null, null, null, null);
    }
}
