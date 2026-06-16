package com.fooddelivery.customer.domain.model.valueobject;

public record IpAddress(String value) {
    public IpAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("IP address cannot be empty");
        }
    }
}
