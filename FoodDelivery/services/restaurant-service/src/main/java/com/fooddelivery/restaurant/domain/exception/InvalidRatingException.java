package com.fooddelivery.restaurant.domain.exception;

import java.math.BigDecimal;

public class InvalidRatingException extends RuntimeException {
    public InvalidRatingException(BigDecimal rating) {
        super("Invalid rating value: " + rating + ". Rating must be between 0 and 5.");
    }
}
