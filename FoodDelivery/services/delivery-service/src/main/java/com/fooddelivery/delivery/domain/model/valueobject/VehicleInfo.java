package com.fooddelivery.delivery.domain.model.valueobject;

public record VehicleInfo(VehicleType type, String licensePlate) {
    public VehicleInfo {
        if (type == null || licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("Vehicle type and license plate cannot be null or empty");
        }
    }
}
