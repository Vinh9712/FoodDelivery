package com.fooddelivery.order.infrastructure.client.dto;

import java.time.Instant;
import java.util.UUID;

/** Response nhận từ Notification Service. */
public record NotificationResponse(
        UUID notificationId,
        String status,
        Instant sentAt,
        String message
) {}
