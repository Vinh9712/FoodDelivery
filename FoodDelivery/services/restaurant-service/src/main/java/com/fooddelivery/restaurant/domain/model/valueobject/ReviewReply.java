package com.fooddelivery.restaurant.domain.model.valueobject;

import java.time.Instant;

public record ReviewReply(String text, Instant repliedAt) {
    public ReviewReply {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Reply text cannot be empty");
        }
        if (repliedAt == null) {
            throw new IllegalArgumentException("Replied timestamp cannot be null");
        }
    }
}
