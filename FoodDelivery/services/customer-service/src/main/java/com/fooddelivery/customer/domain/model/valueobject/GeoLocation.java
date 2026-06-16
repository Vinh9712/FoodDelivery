package com.fooddelivery.customer.domain.model.valueobject;

import java.math.BigDecimal;

public record GeoLocation(BigDecimal latitude, BigDecimal longitude) {
    public GeoLocation {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Latitude and longitude cannot be null");
        }
    }
}
