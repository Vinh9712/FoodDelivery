package com.fooddelivery.order.infrastructure.repository;

import com.fooddelivery.order.domain.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.SpecHints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox repository for durable Kafka relay.
 * <p>
 * Due-id queries are batched (never load the full backlog). Publish path uses
 * pessimistic write with {@code SKIP LOCKED} so multiple relay instances can
 * run safely.
 * </p>
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Legacy helper — unpublished events only (no due-time filter).
     */
    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);

    /**
     * Batch of due unpublished event IDs (not dead-lettered, retry time reached).
     * <p>
     * Only the head of each aggregate's unpublished chain is eligible so later
     * events (e.g. {@code OrderCancelled}) cannot overtake an earlier failed/
     * locked event (e.g. {@code OrderCreated}) on the same aggregate.
     * </p>
     */
    @Query("""
            select event.id from OutboxEvent event
            where event.publishedAt is null
              and event.deadLettered = false
              and (event.nextAttemptAt is null or event.nextAttemptAt <= :now)
              and not exists (
                  select 1 from OutboxEvent earlier
                  where earlier.aggregateType = event.aggregateType
                    and earlier.aggregateId = event.aggregateId
                    and earlier.publishedAt is null
                    and earlier.deadLettered = false
                    and (
                         earlier.createdAt < event.createdAt
                         or (earlier.createdAt = event.createdAt and earlier.id < event.id)
                    )
              )
            order by event.createdAt
            """)
    List<UUID> findDueEventIds(@Param("now") Instant now, Pageable pageable);

    /**
     * Lock a single event for exclusive publish. {@code SKIP LOCKED} so another
     * replica processing the same id does not block.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = SpecHints.HINT_SPEC_LOCK_TIMEOUT, value = "-2"))
    @Query("select event from OutboxEvent event where event.id = :eventId")
    Optional<OutboxEvent> findByIdForUpdate(@Param("eventId") UUID eventId);

    @Query("""
            select count(event) from OutboxEvent event
            where event.publishedAt is null
              and event.deadLettered = false
              and event.attempts = 0
            """)
    long countPending();

    @Query("""
            select count(event) from OutboxEvent event
            where event.publishedAt is null
              and event.deadLettered = false
              and event.attempts > 0
            """)
    long countRetry();

    @Query("""
            select count(event) from OutboxEvent event
            where event.deadLettered = true
            """)
    long countDeadLettered();
}
