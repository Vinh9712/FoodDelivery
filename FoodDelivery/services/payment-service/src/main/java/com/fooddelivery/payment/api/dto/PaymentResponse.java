package com.fooddelivery.payment.api.dto;

import java.util.UUID;

/**
 * Response DTO cho kết quả thanh toán.
 */
public record PaymentResponse(
        UUID orderId,
        String status,       // "SUCCESS" hoặc "FAILED"
        String transactionId, // Mã giao dịch (null nếu thất bại)
        String message        // Thông báo chi tiết
) {}
