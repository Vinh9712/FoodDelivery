package com.fooddelivery.order.infrastructure.client.dto;

import java.util.UUID;

/** Request gửi đến Notification Service. */
public record NotificationRequest(
        UUID orderId,
        UUID customerId,
        String channel,   // "EMAIL", "IN_APP", "SMS"
        String subject,
        String message
) {}
