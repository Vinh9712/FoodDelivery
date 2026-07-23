package com.fooddelivery.payment.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void retryKeepsIdentitySequenceAndSerializedPayloadStable() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        ObjectNode payload = objectMapper.createObjectNode()
                .put("paymentId", paymentId.toString())
                .put("orderId", orderId.toString())
                .put("amount", "125000");
        OutboxEvent event = new OutboxEvent(
                "Payment",
                paymentId,
                "PaymentSucceeded",
                1,
                1L,
                orderId.toString(),
                payload);
        UUID eventId = event.getId();
        long sequence = event.getAggregateSequence();
        var serialized = event.getPayload();

        event.recordFailure("broker unavailable", Instant.now().plusSeconds(5));

        assertThat(event.getId()).isEqualTo(eventId);
        assertThat(event.getAggregateSequence()).isEqualTo(sequence);
        assertThat(event.getPayload()).isEqualTo(serialized);
        assertThat(event.getPartitionKey()).isEqualTo(orderId.toString());
        assertThat(event.getEventVersion()).isEqualTo(1);
    }
}
