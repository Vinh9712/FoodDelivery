package com.fooddelivery.delivery.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import com.fooddelivery.delivery.domain.util.UuidCreator;

/**
 * Outbox event entity — stores domain events for reliable Kafka publishing.
 * Relay process publishes with per-event transactions, retry, and dead-letter.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    private static final AtomicLong LEGACY_SEQUENCE = new AtomicLong();

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "aggregate_sequence", nullable = false)
    private long aggregateSequence;

    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published", nullable = false)
    private boolean published;

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

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
        this(aggregateType, aggregateId, eventType, 1,
                LEGACY_SEQUENCE.incrementAndGet(),
                aggregateId == null ? "unknown" : aggregateId.toString(),
                payload,
                Instant.now());
    }

    public OutboxEvent(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            long aggregateSequence,
            String partitionKey,
            String payload) {
        this(aggregateType, aggregateId, eventType, eventVersion, aggregateSequence, partitionKey, payload,
                Instant.now());
    }

    public OutboxEvent(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            int eventVersion,
            long aggregateSequence,
            String partitionKey,
            String payload,
            Instant occurredAt) {
        this.id = UuidCreator.nextUuidV7();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateSequence = aggregateSequence;
        this.partitionKey = partitionKey;
        this.payload = payload;
        this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
        this.published = false;
        this.attempts = 0;
        this.nextAttemptAt = this.occurredAt;
        this.deadLettered = false;
    }

    public void markPublished() {
        this.published = true;
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

    /** published_at is the canonical success marker; boolean published is kept for migration compatibility. */
    public boolean isPublished() {
        return publishedAt != null || published;
    }

    public boolean canPublish(Instant now) {
        return publishedAt == null && !deadLettered
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown publishing error";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
