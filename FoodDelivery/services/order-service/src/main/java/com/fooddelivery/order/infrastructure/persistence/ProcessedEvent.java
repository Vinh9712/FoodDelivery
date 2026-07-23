package com.fooddelivery.order.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks processed Kafka events for idempotent consumption.
 */
@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEvent.ProcessedEventId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Id
    @Column(name = "consumer", nullable = false, length = 100)
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent(UUID eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = Instant.now();
    }

    @Getter
    @NoArgsConstructor
    public static class ProcessedEventId implements Serializable {
        private UUID eventId;
        private String consumer;

        public ProcessedEventId(UUID eventId, String consumer) {
            this.eventId = eventId;
            this.consumer = consumer;
        }
    }
}
