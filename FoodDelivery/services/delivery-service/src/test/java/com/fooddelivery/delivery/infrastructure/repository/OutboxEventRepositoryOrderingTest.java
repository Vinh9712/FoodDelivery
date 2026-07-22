package com.fooddelivery.delivery.infrastructure.repository;

import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
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

    @Test
    void sequencesForOneAggregateAreReturnedStrictlyInOrder() {
        UUID aggregateId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OutboxEvent first = event(aggregateId, orderId, "DriverAssigned", 1L);
        OutboxEvent second = event(aggregateId, orderId, "DeliveryPickedUp", 2L);
        OutboxEvent third = event(aggregateId, orderId, "DeliveryCompleted", 3L);
        outboxEventRepository.saveAll(List.of(first, second, third));

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));
        assertThat(due).contains(first.getId());
        assertThat(due).doesNotContain(second.getId(), third.getId());

        first.markPublished();
        outboxEventRepository.save(first);
        due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));
        assertThat(due).contains(second.getId());
        assertThat(due).doesNotContain(third.getId());

        second.markPublished();
        outboxEventRepository.save(second);
        due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));
        assertThat(due).contains(third.getId());
    }

    @Test
    void retryDelayedHeadBlocksLaterSibling() {
        UUID aggregateId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OutboxEvent first = event(aggregateId, orderId, "DriverAssigned", 1L);
        OutboxEvent second = event(aggregateId, orderId, "DeliveryPickedUp", 2L);
        first.recordFailure("Kafka unavailable", Instant.now().plusSeconds(300));
        outboxEventRepository.save(first);
        outboxEventRepository.save(second);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).doesNotContain(first.getId(), second.getId());
    }

    @Test
    void deadLetteredEarlierEventBlocksLaterSibling() {
        UUID aggregateId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OutboxEvent first = event(aggregateId, orderId, "DriverAssigned", 1L);
        OutboxEvent second = event(aggregateId, orderId, "DeliveryPickedUp", 2L);
        first.markDeadLettered("poison");
        outboxEventRepository.save(first);
        outboxEventRepository.save(second);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).doesNotContain(first.getId(), second.getId());
    }

    @Test
    void differentAggregateIsNotBlockedByUnpublishedHeadOnAnotherAggregate() {
        UUID blocked = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        OutboxEvent blockedHead = event(blocked, UUID.randomUUID(), "DriverAssigned", 1L);
        OutboxEvent freeHead = event(free, UUID.randomUUID(), "DriverAssigned", 1L);
        blockedHead.recordFailure("down", Instant.now().plusSeconds(300));
        outboxEventRepository.save(blockedHead);
        outboxEventRepository.save(freeHead);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).contains(freeHead.getId());
        assertThat(due).doesNotContain(blockedHead.getId());
    }

    private OutboxEvent event(UUID deliveryId, UUID orderId, String eventType, long sequence) {
        String payload = """
                {"orderId":"%s","deliveryId":"%s"}
                """.formatted(orderId, deliveryId).trim();
        return new OutboxEvent(
                "Delivery",
                deliveryId,
                eventType,
                1,
                sequence,
                orderId.toString(),
                payload);
    }
}
