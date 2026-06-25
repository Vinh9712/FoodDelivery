package com.fooddelivery.order.infrastructure.client.dto;

import java.util.UUID;

/** Response nhận từ Payment Service. */
public record PaymentResponse(
        UUID orderId,
        String status,        // "SUCCESS" hoặc "FAILED"
        String transactionId,
        String message
) {
    /** Kiểm tra thanh toán thành công */
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(status);
    }
}
