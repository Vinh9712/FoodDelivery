package com.fooddelivery.restaurant.domain.model.valueobject;

import java.time.LocalTime;

public record OpeningHours(LocalTime openTime, LocalTime closeTime) {
    public OpeningHours {
        if (openTime == null || closeTime == null) {
            throw new IllegalArgumentException("Opening and closing times cannot be null");
        }
    }
    
    public boolean isOpenAt(LocalTime time) {
        if (openTime.isBefore(closeTime)) {
            return !time.isBefore(openTime) && !time.isAfter(closeTime);
        } else {
            return !time.isBefore(openTime) || !time.isAfter(closeTime);
        }
    }
}
