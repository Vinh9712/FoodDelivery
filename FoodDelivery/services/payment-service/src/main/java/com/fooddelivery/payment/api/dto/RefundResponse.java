package com.fooddelivery.payment.api.dto;

import java.util.UUID;

/**
 * Response DTO cho kết quả hoàn tiền.
 */
public record RefundResponse(
        UUID orderId,
        String status,  // "REFUNDED"
        String message
) {}
