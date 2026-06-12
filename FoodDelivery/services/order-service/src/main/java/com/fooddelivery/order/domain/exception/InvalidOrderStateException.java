package com.fooddelivery.order.domain.exception;

/**
 * Thrown when an operation is attempted on an Order in an invalid state.
 */
public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
