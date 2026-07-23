package com.fooddelivery.delivery.domain.exception;

public class DeliveryAccessDeniedException extends RuntimeException {
    public DeliveryAccessDeniedException(String message) {
        super(message);
    }
}
