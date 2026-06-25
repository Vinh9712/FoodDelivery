package com.fooddelivery.restaurant.domain.exception;

import java.util.UUID;

public class ReviewAlreadyRepliedException extends RuntimeException {
    public ReviewAlreadyRepliedException(UUID reviewId) {
        super("Review has already been replied to: " + reviewId);
    }
}
