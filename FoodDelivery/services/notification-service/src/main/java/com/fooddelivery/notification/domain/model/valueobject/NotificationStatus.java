package com.fooddelivery.notification.domain.model.valueobject;

public enum NotificationStatus {
    PENDING,
    SENDING,
    RETRY_SCHEDULED,
    SENT,
    DEAD_LETTER
}
