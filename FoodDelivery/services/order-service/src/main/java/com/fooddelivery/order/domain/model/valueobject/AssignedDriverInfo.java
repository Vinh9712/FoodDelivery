package com.fooddelivery.order.domain.model.valueobject;

import com.fooddelivery.order.application.dto.DriverAssignedPayload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of the driver assigned to deliver this order.
 * Populated from the {@code driver.assigned} Kafka event published by Delivery Service.
 */
public record AssignedDriverInfo(
        UUID driverId,
        String fullName,
        String phone,
        VehicleType vehicleType,
        String licensePlate,
        BigDecimal avgRating,
        Instant assignedAt
) {
    /**
     * Factory method to create an AssignedDriverInfo from a Kafka event payload.
     */
    public static AssignedDriverInfo from(DriverAssignedPayload payload) {
        return new AssignedDriverInfo(
                payload.driver().driverId(),
                payload.driver().fullName(),
                payload.driver().phone(),
                VehicleType.valueOf(payload.driver().vehicleType()),
                payload.driver().licensePlate(),
                payload.driver().avgRating(),
                payload.assignedAt()
        );
    }
}
