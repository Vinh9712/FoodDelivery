package com.fooddelivery.payment.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;
import com.fooddelivery.payment.domain.util.UuidCreator;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Type(JsonType.class)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

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

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, JsonNode payload) {
        this.id = UuidCreator.nextUuidV7();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = Instant.now();
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

    public boolean canPublish(Instant now) {
        return !published && !deadLettered
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    private String truncate(String value) {
        if (value == null) {
            return "Unknown publishing error";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }
}
