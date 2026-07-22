package com.fooddelivery.commonevents;

import java.time.Instant;
import java.util.UUID;

public record IntegrationEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String aggregateType,
        UUID aggregateId,
        long aggregateSequence,
        T payload) {

    public IntegrationEventEnvelope {
        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        if (eventVersion != 1) throw new IllegalArgumentException("eventVersion must be 1");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
        if (aggregateType == null || aggregateType.isBlank()) throw new IllegalArgumentException("aggregateType is required");
        if (aggregateId == null) throw new IllegalArgumentException("aggregateId is required");
        if (aggregateSequence < 1) throw new IllegalArgumentException("aggregateSequence must be positive");
        if (payload == null) throw new IllegalArgumentException("payload is required");
    }
}
