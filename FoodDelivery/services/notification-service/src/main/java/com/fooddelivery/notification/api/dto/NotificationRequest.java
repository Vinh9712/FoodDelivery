package com.fooddelivery.notification.api.dto;

import java.util.UUID;

/**
 * Request DTO cho gửi thông báo.
 */
public record NotificationRequest(
        UUID orderId,
        UUID customerId,
        String channel,   // "EMAIL", "IN_APP", "SMS"
        String subject,
        String message
) {}
