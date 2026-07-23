package com.fooddelivery.order.domain.model.valueobject;

import java.math.BigDecimal;

public record DeliveryAddressSnapshot(
        String addressLine,
        String district,
        String city,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public DeliveryAddressSnapshot {
        if (addressLine == null || district == null || city == null) {
            throw new IllegalArgumentException("Address line, district, and city cannot be null");
        }
    }
}
