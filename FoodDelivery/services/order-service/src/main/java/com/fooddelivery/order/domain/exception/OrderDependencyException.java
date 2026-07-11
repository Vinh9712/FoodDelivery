package com.fooddelivery.order.domain.exception;

public class OrderDependencyException extends RuntimeException {
    public OrderDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
