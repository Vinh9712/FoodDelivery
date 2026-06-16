package com.fooddelivery.customer.domain.model.valueobject;

public record TokenHash(String value) {
    public TokenHash {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Token hash cannot be empty");
        }
    }
}
