package com.fooddelivery.payment.infrastructure.repository;

import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
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

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByPublishedFalseOrderByOccurredAtAsc();

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);

    /**
     * Due event IDs only for the head of each aggregate's unpublished chain so
     * later events cannot overtake earlier unpublished ones (including dead-lettered).
     * published_at is the canonical success marker.
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
                    and earlier.aggregateSequence < event.aggregateSequence
              )
            order by event.occurredAt, event.id
            """)
    List<UUID> findDueEventIds(@Param("now") Instant now, Pageable pageable);

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

    @Query("""
            select min(event.occurredAt) from OutboxEvent event
            where event.publishedAt is null
              and event.deadLettered = false
            """)
    Instant findOldestUnpublishedOccurredAt();
}
