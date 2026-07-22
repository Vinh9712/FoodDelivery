package com.fooddelivery.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO cho kết quả hoàn tiền (idempotent replay-safe).
 */
public record RefundResponse(
        UUID orderId,
        String status,
        String message,
        UUID paymentId,
        UUID refundId,
        BigDecimal amount,
        Instant refundedAt
) {
    public RefundResponse(UUID orderId, String status, String message) {
        this(orderId, status, message, null, null, null, null);
    }
}
