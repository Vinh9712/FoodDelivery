package com.fooddelivery.delivery.domain.model.valueobject;

/**
 * Delivery lifecycle statuses.
 */
public enum DeliveryStatus {
    PENDING,
    FINDING_DRIVER,
    DRIVER_ASSIGNED,
    PICKED_UP,
    DELIVERING,
    DELIVERED,
    CANCELLED,
    FAILED
}
