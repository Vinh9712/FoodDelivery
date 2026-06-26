package com.fooddelivery.customer.domain.model.valueobject;

public record FullName(String value) {
    public FullName {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        value = value.trim();
        if (value.length() > 150) {
            throw new IllegalArgumentException("Full name must be at most 150 characters");
        }
    }
}
