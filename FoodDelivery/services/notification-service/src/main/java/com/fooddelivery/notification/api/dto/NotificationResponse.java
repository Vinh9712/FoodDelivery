package com.fooddelivery.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO cho kết quả gửi thông báo.
 */
public record NotificationResponse(
        UUID notificationId,
        String status,  // "SENT"
        Instant sentAt,
        String message
) {}
