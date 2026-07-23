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
 * Ensures due-event selection only returns the head of each aggregate chain.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryOrderingTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void findDueEventIdsOnlyReturnsHeadPerAggregateWhenEarlierEventIsBlocked() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent created = OutboxEvent.create(
                "Order", aggregateId, "OrderCreated", Map.of("step", "1"));
        OutboxEvent cancelled = OutboxEvent.create(
                "Order", aggregateId, "OrderCancelled", Map.of("step", "2"));
        // Block the earlier event so it is not due; later must not be selected either.
        created.recordFailure("Kafka unavailable", Instant.now().plusSeconds(300));
        outboxEventRepository.save(created);
        outboxEventRepository.save(cancelled);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).doesNotContain(created.getId(), cancelled.getId());
    }

    @Test
    void findDueEventIdsReturnsHeadWhenDueAndBlocksLaterSibling() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent created = OutboxEvent.create(
                "Order", aggregateId, "OrderCreated", Map.of("step", "1"));
        OutboxEvent cancelled = OutboxEvent.create(
                "Order", aggregateId, "OrderCancelled", Map.of("step", "2"));
        outboxEventRepository.save(created);
        outboxEventRepository.save(cancelled);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).contains(created.getId());
        assertThat(due).doesNotContain(cancelled.getId());
    }

    @Test
    void findDueEventIdsAllowsLaterEventAfterHeadIsPublished() {
        UUID aggregateId = UUID.randomUUID();
        OutboxEvent created = OutboxEvent.create(
                "Order", aggregateId, "OrderCreated", Map.of("step", "1"));
        OutboxEvent cancelled = OutboxEvent.create(
                "Order", aggregateId, "OrderCancelled", Map.of("step", "2"));
        created.markPublished();
        outboxEventRepository.save(created);
        outboxEventRepository.save(cancelled);

        List<UUID> due = outboxEventRepository.findDueEventIds(Instant.now(), PageRequest.of(0, 50));

        assertThat(due).contains(cancelled.getId());
        assertThat(due).doesNotContain(created.getId());
    }
}
