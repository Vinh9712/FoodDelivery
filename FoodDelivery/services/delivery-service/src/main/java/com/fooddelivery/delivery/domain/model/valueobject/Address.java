package com.fooddelivery.delivery.domain.model.valueobject;

import java.math.BigDecimal;

public record Address(String text, BigDecimal latitude, BigDecimal longitude) {
    public Address {
        if (text == null) {
            throw new IllegalArgumentException("Address text cannot be null");
        }
    }
}
