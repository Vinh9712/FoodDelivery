package com.fooddelivery.customer.domain.model.valueobject;

public record AddressLabel(String value) {
    public AddressLabel {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Address label cannot be empty");
        }
    }
}
