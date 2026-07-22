package com.fooddelivery.order.infrastructure.persistence;

import com.fooddelivery.order.domain.util.UuidCreator;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Gap buffer for out-of-order integration events waiting on a missing predecessor sequence.
 */
@Entity
@Table(name = "deferred_integration_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeferredIntegrationEvent {

    public enum Status {
        WAITING_FOR_PREDECESSOR,
        DEAD_LETTER
    }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_sequence", nullable = false)
    private long aggregateSequence;

    @Type(JsonType.class)
    @Column(name = "event_json", columnDefinition = "jsonb", nullable = false)
    private String eventJson;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    public static DeferredIntegrationEvent waiting(
            String consumerName,
            UUID eventId,
            String aggregateType,
            UUID aggregateId,
            long aggregateSequence,
            String eventJson,
            Instant receivedAt) {
        DeferredIntegrationEvent event = new DeferredIntegrationEvent();
        event.id = UuidCreator.nextUuidV7();
        event.consumerName = consumerName;
        event.eventId = eventId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.aggregateSequence = aggregateSequence;
        event.eventJson = eventJson;
        event.receivedAt = receivedAt;
        event.status = Status.WAITING_FOR_PREDECESSOR;
        event.attempts = 0;
        event.nextAttemptAt = receivedAt;
        return event;
    }

    public void scheduleRetry(Instant nextAttemptAt, String error) {
        this.attempts += 1;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = truncate(error);
    }

    public void markDeadLetter(Instant now, String error) {
        this.status = Status.DEAD_LETTER;
        this.deadLetteredAt = now;
        this.lastError = truncate(error);
        this.nextAttemptAt = now;
    }

    public boolean isWaiting() {
        return status == Status.WAITING_FOR_PREDECESSOR;
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
