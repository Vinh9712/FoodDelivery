package com.fooddelivery.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing the assigned driver in API responses.
 */
public record AssignedDriverDto(
        UUID driverId,
        String fullName,
        String phone,
        String vehicleType,
        String licensePlate,
        BigDecimal avgRating,
        Instant assignedAt
) {}
