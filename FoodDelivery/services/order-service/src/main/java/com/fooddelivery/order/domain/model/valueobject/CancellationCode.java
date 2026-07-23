package com.fooddelivery.order.domain.model.valueobject;

public enum CancellationCode {
    CUSTOMER_REQUESTED,
    RESTAURANT_REJECTED,
    RESTAURANT_ACCEPTANCE_TIMEOUT,
    DELIVERY_FAILED,
    /** Customer cancelled before pickup / kitchen too late. */
    CUSTOMER_REQUESTED,
    /** Admin force-cancel. */
    ADMIN_CANCELLED
}
