package com.fooddelivery.customer.domain.model.valueobject;

public record FullName(String value) {
    public FullName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Full name cannot be empty");
        }
    }
}
