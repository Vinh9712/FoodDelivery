package com.fooddelivery.payment.domain.model.valueobject;

public record RequestHash(String value) {
    public RequestHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Request hash cannot be empty");
        }
    }
}
