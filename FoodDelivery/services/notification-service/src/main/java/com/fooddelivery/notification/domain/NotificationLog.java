package com.fooddelivery.notification.domain;

import java.time.Instant;

/**
 * Immutable record representing a notification log entry.
 */
public record NotificationLog(
        String id,
        String eventType,
        String topic,
        String summary,
        Instant receivedAt
) {}
