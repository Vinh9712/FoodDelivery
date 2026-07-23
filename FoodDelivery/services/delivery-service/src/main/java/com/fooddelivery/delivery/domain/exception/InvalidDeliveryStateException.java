package com.fooddelivery.delivery.domain.exception;

import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;

public class InvalidDeliveryStateException extends RuntimeException {
    public InvalidDeliveryStateException(DeliveryStatus status) {
        super("Invalid delivery state: " + status);
    }
}
