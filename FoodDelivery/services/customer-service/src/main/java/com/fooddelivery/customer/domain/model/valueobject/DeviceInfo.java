package com.fooddelivery.customer.domain.model.valueobject;

public record DeviceInfo(String value) {
    public DeviceInfo {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Device info cannot be empty");
        }
    }
}
