package com.fooddelivery.restaurant.domain.exception;

public class InvalidRestaurantStateException extends RuntimeException {
    public InvalidRestaurantStateException(String message) {
        super(message);
    }
}
