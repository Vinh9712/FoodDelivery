package com.fooddelivery.delivery.api.dto;

import com.fooddelivery.delivery.domain.model.DeliveryTracking;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TrackingPointResponse(
        UUID id,
        BigDecimal latitude,
        BigDecimal longitude,
        DeliveryStatus statusSnapshot,
        Instant recordedAt
) {
    public static TrackingPointResponse from(DeliveryTracking point) {
        return new TrackingPointResponse(
                point.getId(),
                point.getLatitude(),
                point.getLongitude(),
                point.getStatusSnapshot(),
                point.getRecordedAt());
    }
}
