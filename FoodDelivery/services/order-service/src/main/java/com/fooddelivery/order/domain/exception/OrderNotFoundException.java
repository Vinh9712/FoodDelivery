package com.fooddelivery.order.domain.exception;

import java.util.UUID;

/**
 * Thrown when an Order cannot be found by its ID.
 */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}
