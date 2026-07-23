package com.fooddelivery.order.domain.model.valueobject;

/**
 * Order lifecycle statuses.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    PICKED_UP,
    DELIVERING,
    DELIVERED,
    CANCELLATION_PENDING,
    CANCELLED
}
