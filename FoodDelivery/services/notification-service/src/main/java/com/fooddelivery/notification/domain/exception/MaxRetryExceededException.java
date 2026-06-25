package com.fooddelivery.notification.domain.exception;

import java.util.UUID;

public class MaxRetryExceededException extends RuntimeException {
    public MaxRetryExceededException(UUID notificationId) {
        super("Notification retry count exceeded maximum limit: " + notificationId);
    }
}
