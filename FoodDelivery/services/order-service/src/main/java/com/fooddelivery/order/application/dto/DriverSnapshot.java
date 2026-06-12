package com.fooddelivery.order.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Snapshot of the driver included in the {@code driver.assigned} Kafka event.
 */
public record DriverSnapshot(
        UUID driverId,
        String fullName,
        String phone,
        String vehicleType,
        String licensePlate,
        BigDecimal avgRating
) {}
