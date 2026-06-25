package com.fooddelivery.order.infrastructure.client.dto;

import java.util.UUID;

/** Response nhận từ Delivery Service. */
public record DeliveryResponse(
        UUID orderId,
        String status,   // "ASSIGNED" hoặc "FAILED"
        UUID driverId,
        String message
) {
    /** Kiểm tra phân bổ tài xế thành công */
    public boolean isAssigned() {
        return "ASSIGNED".equalsIgnoreCase(status);
    }
}
