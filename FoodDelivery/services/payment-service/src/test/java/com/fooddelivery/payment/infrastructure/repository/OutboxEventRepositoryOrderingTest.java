package com.fooddelivery.payment.infrastructure.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryOrderingTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sequencesForOneAggregateAreReturnedStrictlyInOrder() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OutboxEvent first = event(paymentId, orderId, "PaymentSucceeded", 1L);
        OutboxEvent second = event(paymentId, orderId, "PaymentRefunded", 2L);
        outboxEventRepository.saveAll(List.of(first, second));

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));
        assertThat(due).contains(first.getId());
        assertThat(due).doesNotContain(second.getId());

        first.markPublished();
        outboxEventRepository.save(first);
        due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));
        assertThat(due).contains(second.getId());
        assertThat(due).doesNotContain(first.getId());
    }

    @Test
    void retryDelayedHeadBlocksLaterSibling() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OutboxEvent first = event(paymentId, orderId, "PaymentSucceeded", 1L);
        OutboxEvent second = event(paymentId, orderId, "PaymentRefunded", 2L);
        first.recordFailure("Kafka unavailable", Instant.now().plusSeconds(300));
        outboxEventRepository.save(first);
        outboxEventRepository.save(second);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).doesNotContain(first.getId(), second.getId());
    }

    @Test
    void deadLetteredEarlierEventBlocksLaterSibling() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OutboxEvent first = event(paymentId, orderId, "PaymentSucceeded", 1L);
        OutboxEvent second = event(paymentId, orderId, "PaymentRefunded", 2L);
        first.markDeadLettered("poison");
        outboxEventRepository.save(first);
        outboxEventRepository.save(second);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).doesNotContain(first.getId(), second.getId());
    }

    @Test
    void differentAggregateIsNotBlockedByUnpublishedHeadOnAnotherAggregate() {
        OutboxEvent blockedHead = event(UUID.randomUUID(), UUID.randomUUID(), "PaymentSucceeded", 1L);
        OutboxEvent freeHead = event(UUID.randomUUID(), UUID.randomUUID(), "PaymentSucceeded", 1L);
        blockedHead.recordFailure("down", Instant.now().plusSeconds(300));
        outboxEventRepository.save(blockedHead);
        outboxEventRepository.save(freeHead);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).contains(freeHead.getId());
        assertThat(due).doesNotContain(blockedHead.getId());
    }

    private OutboxEvent event(UUID paymentId, UUID orderId, String eventType, long sequence) {
        return new OutboxEvent(
                "Payment",
                paymentId,
                eventType,
                1,
                sequence,
                orderId.toString(),
                objectMapper.createObjectNode()
                        .put("paymentId", paymentId.toString())
                        .put("orderId", orderId.toString())
                        .put("step", String.valueOf(sequence)));
    }
}
