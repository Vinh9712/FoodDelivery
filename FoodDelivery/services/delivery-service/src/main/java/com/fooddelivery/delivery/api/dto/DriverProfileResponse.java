package com.fooddelivery.delivery.api.dto;

import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DriverStatus;
import com.fooddelivery.delivery.domain.model.valueobject.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DriverProfileResponse(
        UUID driverId,
        UUID userId,
        String fullName,
        String phone,
        VehicleType vehicleType,
        String licensePlate,
        BigDecimal avgRating,
        int totalReviews,
        boolean online,
        boolean available,
        DriverStatus status,
        BigDecimal currentLatitude,
        BigDecimal currentLongitude,
        Instant locationUpdatedAt
) {
    public static DriverProfileResponse from(Driver driver) {
        return new DriverProfileResponse(
                driver.getId(),
                driver.getUserId(),
                driver.getFullName(),
                driver.getPhone(),
                driver.getVehicleType(),
                driver.getLicensePlate(),
                driver.getAvgRating(),
                driver.getTotalReviews(),
                driver.isOnline(),
                driver.isAvailable(),
                driver.getStatus(),
                driver.getCurrentLatitude(),
                driver.getCurrentLongitude(),
                driver.getLocationUpdatedAt());
    }
}
