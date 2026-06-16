package com.fooddelivery.delivery.domain.exception;

import java.math.BigDecimal;

public class InvalidRatingException extends RuntimeException {
    public InvalidRatingException(BigDecimal rating) {
        super("Invalid rating: " + rating);
    }
}
