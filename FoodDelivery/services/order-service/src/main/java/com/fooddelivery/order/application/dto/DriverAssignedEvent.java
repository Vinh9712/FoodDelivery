package com.fooddelivery.order.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Top-level envelope for the {@code driver.assigned} Kafka event.
 */
public record DriverAssignedEvent(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        DriverAssignedPayload payload
) {}
