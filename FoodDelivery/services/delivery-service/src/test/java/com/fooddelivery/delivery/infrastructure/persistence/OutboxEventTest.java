package com.fooddelivery.delivery.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void retryKeepsIdentitySequenceAndSerializedPayloadStable() {
        UUID deliveryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String payload = """
                {"orderId":"%s","deliveryId":"%s"}
                """.formatted(orderId, deliveryId).trim();
        OutboxEvent event = new OutboxEvent(
                "Delivery",
                deliveryId,
                "DeliveryCompleted",
                1,
                2L,
                orderId.toString(),
                payload);
        UUID eventId = event.getId();
        long sequence = event.getAggregateSequence();
        String serialized = event.getPayload();

        event.recordFailure("broker unavailable", Instant.now().plusSeconds(5));

        assertThat(event.getId()).isEqualTo(eventId);
        assertThat(event.getAggregateSequence()).isEqualTo(sequence);
        assertThat(event.getPayload()).isEqualTo(serialized);
        assertThat(event.getPartitionKey()).isEqualTo(orderId.toString());
        assertThat(event.getEventVersion()).isEqualTo(1);
    }

    @Test
    void markPublishedSetsCanonicalPublishedAtAndBoolean() {
        OutboxEvent event = new OutboxEvent(
                "Delivery",
                UUID.randomUUID(),
                "DeliveryPickedUp",
                1,
                1L,
                UUID.randomUUID().toString(),
                "{}");

        event.markPublished();

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.canPublish(Instant.now())).isFalse();
    }
}
