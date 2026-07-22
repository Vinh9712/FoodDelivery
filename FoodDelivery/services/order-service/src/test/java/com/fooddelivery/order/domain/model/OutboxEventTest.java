package com.fooddelivery.order.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void createIsImmediatelyDueAndUnpublished() {
        Instant before = Instant.now().minusSeconds(1);
        OutboxEvent event = OutboxEvent.create(
                "Order", UUID.randomUUID(), "OrderCreated", Map.of("k", "v"));

        assertThat(event.isPublished()).isFalse();
        assertThat(event.isDeadLettered()).isFalse();
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isNotNull();
        assertThat(event.canPublish(Instant.now())).isTrue();
        assertThat(event.getCreatedAt()).isAfter(before);
    }

    @Test
    void retryKeepsIdentitySequenceAndSerializedPayloadStable() {
        UUID orderId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "orderId", orderId.toString(),
                "step", "1");
        OutboxEvent event = OutboxEvent.create(
                "Order", orderId, "OrderCreated", 1, 1L, orderId.toString(), payload);
        UUID eventId = event.getId();
        long sequence = event.getAggregateSequence();
        Map<String, Object> serialized = event.getPayload();

        event.recordFailure("broker unavailable", Instant.now().plusSeconds(5));

        assertThat(event.getId()).isEqualTo(eventId);
        assertThat(event.getAggregateSequence()).isEqualTo(sequence);
        assertThat(event.getPayload()).isEqualTo(serialized);
        assertThat(event.getPartitionKey()).isEqualTo(orderId.toString());
        assertThat(event.getEventVersion()).isEqualTo(1);
    }

    @Test
    void recordFailureIncrementsAttemptsAndSchedulesRetry() {
        OutboxEvent event = OutboxEvent.create(
                "Order", UUID.randomUUID(), "OrderCreated", Map.of());
        Instant retryAt = Instant.now().plusSeconds(30);

        event.recordFailure("boom", retryAt);

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("boom");
        assertThat(event.getNextAttemptAt()).isEqualTo(retryAt);
        assertThat(event.canPublish(Instant.now())).isFalse();
        assertThat(event.canPublish(retryAt.plusSeconds(1))).isTrue();
    }

    @Test
    void markDeadLetteredStopsPublishing() {
        OutboxEvent event = OutboxEvent.create(
                "Order", UUID.randomUUID(), "OrderCreated", Map.of());

        event.markDeadLettered("fatal");

        assertThat(event.isDeadLettered()).isTrue();
        assertThat(event.getDeadLetteredAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.canPublish(Instant.now())).isFalse();
    }
}
