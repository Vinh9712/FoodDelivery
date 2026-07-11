package com.fooddelivery.order.domain.exception;

public class InvalidOrderRequestException extends RuntimeException {
    public InvalidOrderRequestException(String message) {
        super(message);
    }

    public InvalidOrderRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
