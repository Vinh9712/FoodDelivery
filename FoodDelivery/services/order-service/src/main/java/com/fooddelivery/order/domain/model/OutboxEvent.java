package com.fooddelivery.order.domain.model;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.fooddelivery.order.domain.util.UuidCreator;


@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final AtomicLong LEGACY_SEQUENCE = new AtomicLong();

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Aggregate root type, e.g. {@code "Order"} */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /** Aggregate root id */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /** Event type, e.g. {@code "OrderCreated"} */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "aggregate_sequence", nullable = false)
    private long aggregateSequence;

    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    /** Event payload (JSONB) */
    @Type(JsonType.class)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;

    /** Broker publish time; null while unpublished */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "dead_lettered", nullable = false)
    private boolean deadLettered;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Factory for a new unpublished outbox event (immediately due).
     */
    public static OutboxEvent create(String aggregateType, UUID aggregateId,
                                     String eventType, Map<String, Object> payload) {
        return create(aggregateType, aggregateId, eventType, 1,
                LEGACY_SEQUENCE.incrementAndGet(), aggregateId.toString(), payload);
    }

    public static OutboxEvent create(String aggregateType, UUID aggregateId,
                                     String eventType, int eventVersion,
                                     long aggregateSequence, String partitionKey,
                                     Map<String, Object> payload) {
        if (aggregateType == null || aggregateType.isBlank()) throw new IllegalArgumentException("aggregateType is required");
        if (aggregateId == null) throw new IllegalArgumentException("aggregateId is required");
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType is required");
        if (eventVersion != 1) throw new IllegalArgumentException("eventVersion must be 1");
        if (aggregateSequence < 1) throw new IllegalArgumentException("aggregateSequence must be positive");
        if (partitionKey == null || partitionKey.isBlank()) throw new IllegalArgumentException("partitionKey is required");
        if (payload == null) throw new IllegalArgumentException("payload is required");
        var event = new OutboxEvent();
        event.id = UuidCreator.nextUuidV7();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.aggregateSequence = aggregateSequence;
        event.partitionKey = partitionKey;
        event.payload = payload;
        event.publishedAt = null;
        event.attempts = 0;
        event.createdAt = Instant.now();
        event.nextAttemptAt = event.createdAt;
        event.deadLettered = false;
        return event;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public long getAggregateSequence() { return aggregateSequence; }
    public String getPartitionKey() { return partitionKey; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public boolean isDeadLettered() { return deadLettered; }
    public Instant getDeadLetteredAt() { return deadLetteredAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void recordFailure(String error, Instant retryAt) {
        this.attempts++;
        this.lastError = truncate(error);
        this.nextAttemptAt = retryAt;
    }

    public void markDeadLettered(String error) {
        this.attempts++;
        this.lastError = truncate(error);
        this.deadLettered = true;
        this.deadLetteredAt = Instant.now();
        this.nextAttemptAt = null;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public boolean canPublish(Instant now) {
        return publishedAt == null
                && !deadLettered
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown publishing error";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
