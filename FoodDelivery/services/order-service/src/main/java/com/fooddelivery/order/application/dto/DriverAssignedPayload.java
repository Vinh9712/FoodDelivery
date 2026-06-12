package com.fooddelivery.order.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Payload of the {@code driver.assigned} Kafka event.
 */
public record DriverAssignedPayload(
        UUID orderId,
        UUID deliveryId,
        DriverSnapshot driver,
        Instant assignedAt
) {}
