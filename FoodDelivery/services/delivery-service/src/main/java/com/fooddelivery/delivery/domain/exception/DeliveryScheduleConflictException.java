package com.fooddelivery.delivery.domain.exception;

import java.util.UUID;

/**
 * Raised when a schedule request for an existing order carries a different immutable snapshot hash.
 */
public class DeliveryScheduleConflictException extends RuntimeException {
    public DeliveryScheduleConflictException(UUID orderId) {
        super("Delivery schedule request conflicts with existing delivery for order " + orderId);
    }
}
