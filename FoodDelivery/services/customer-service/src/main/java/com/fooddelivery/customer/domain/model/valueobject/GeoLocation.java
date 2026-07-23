package com.fooddelivery.customer.domain.model.valueobject;

import java.math.BigDecimal;

public record GeoLocation(BigDecimal latitude, BigDecimal longitude) {
    public GeoLocation {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together");
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    public static GeoLocation ofNullable(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null && longitude == null) {
            return null;
        }
        return new GeoLocation(latitude, longitude);
    }
}
