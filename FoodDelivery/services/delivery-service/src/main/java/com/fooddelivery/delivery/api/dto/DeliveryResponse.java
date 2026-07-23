package com.fooddelivery.delivery.api.dto;

import java.util.UUID;

/**
 * Response DTO cho kết quả lập lịch giao vận.
 */
public record DeliveryResponse(
        UUID orderId,
        String status,    // "ASSIGNED" hoặc "FAILED"
        UUID driverId,    // ID tài xế (null nếu thất bại)
        String message
) {}
