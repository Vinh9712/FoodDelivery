package com.fooddelivery.customer.domain.model.valueobject;

public record Phone(String value) {
    public Phone {
        if (value != null && !value.matches("^0\\d{9,10}$")) {
            throw new IllegalArgumentException("Invalid phone number format: " + value);
        }
    }
}
