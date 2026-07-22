package com.fooddelivery.order.infrastructure.repository;

import com.fooddelivery.order.domain.model.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures due-event selection returns ordered heads with head-of-line blocking,
 * including when earlier rows are delayed or dead-lettered.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryOrderingTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void sequencesForOneAggregateAreReturnedStrictlyInOrder() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent first = event(aggregateId, "OrderCreated", 1L);
        OutboxEvent second = event(aggregateId, "OrderStatusChanged", 2L);
        OutboxEvent third = event(aggregateId, "OrderCancelled", 3L);
        outboxEventRepository.saveAll(List.of(first, second, third));

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));
        assertThat(due).contains(first.getId());
        assertThat(due).doesNotContain(second.getId(), third.getId());

        first.markPublished();
        outboxEventRepository.save(first);
        due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));
        assertThat(due).contains(second.getId());
        assertThat(due).doesNotContain(first.getId(), third.getId());

        second.markPublished();
        outboxEventRepository.save(second);
        due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));
        assertThat(due).contains(third.getId());
        assertThat(due).doesNotContain(first.getId(), second.getId());
    }

    @Test
    void findDueEventIdsOnlyReturnsHeadPerAggregateWhenEarlierEventIsBlocked() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent created = event(aggregateId, "OrderCreated", 1L);
        OutboxEvent cancelled = event(aggregateId, "OrderCancelled", 2L);
        created.recordFailure("Kafka unavailable", Instant.now().plusSeconds(300));
        outboxEventRepository.save(created);
        outboxEventRepository.save(cancelled);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).doesNotContain(created.getId(), cancelled.getId());
    }

    @Test
    void deadLetteredEarlierEventBlocksLaterSibling() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent created = event(aggregateId, "OrderCreated", 1L);
        OutboxEvent cancelled = event(aggregateId, "OrderCancelled", 2L);
        created.markDeadLettered("poison");
        outboxEventRepository.save(created);
        outboxEventRepository.save(cancelled);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).doesNotContain(created.getId(), cancelled.getId());
    }

    @Test
    void findDueEventIdsReturnsHeadWhenDueAndBlocksLaterSibling() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent created = event(aggregateId, "OrderCreated", 1L);
        OutboxEvent cancelled = event(aggregateId, "OrderCancelled", 2L);
        outboxEventRepository.save(created);
        outboxEventRepository.save(cancelled);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).contains(created.getId());
        assertThat(due).doesNotContain(cancelled.getId());
    }

    @Test
    void findDueEventIdsAllowsLaterEventAfterHeadIsPublished() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent created = event(aggregateId, "OrderCreated", 1L);
        OutboxEvent cancelled = event(aggregateId, "OrderCancelled", 2L);
        created.markPublished();
        outboxEventRepository.save(created);
        outboxEventRepository.save(cancelled);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).contains(cancelled.getId());
        assertThat(due).doesNotContain(created.getId());
    }

    @Test
    void differentAggregateIsNotBlockedByUnpublishedHeadOnAnotherAggregate() {
        UUID blocked = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        OutboxEvent blockedHead = event(blocked, "OrderCreated", 1L);
        OutboxEvent freeHead = event(free, "OrderCreated", 1L);
        blockedHead.recordFailure("down", Instant.now().plusSeconds(300));
        outboxEventRepository.save(blockedHead);
        outboxEventRepository.save(freeHead);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).contains(freeHead.getId());
        assertThat(due).doesNotContain(blockedHead.getId());
    }

    private OutboxEvent event(UUID aggregateId, String eventType, long sequence) {
        return OutboxEvent.create(
                "Order",
                aggregateId,
                eventType,
                1,
                sequence,
                aggregateId.toString(),
                Map.of("step", String.valueOf(sequence)));
    }
}
