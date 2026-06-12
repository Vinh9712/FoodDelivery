package com.fooddelivery.delivery.domain.model.valueobject;

/**
 * Delivery lifecycle statuses.
 */
public enum DeliveryStatus {
    PENDING,
    DRIVER_ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}
