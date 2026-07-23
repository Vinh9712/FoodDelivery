package com.fooddelivery.payment.infrastructure.repository;

import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByPublishedFalseOrderByOccurredAtAsc();

    /**
     * Due event IDs only for the head of each aggregate's unpublished chain so
     * later events cannot overtake earlier unpublished ones on the same aggregate.
     */
    @Query("""
            select event.id from OutboxEvent event
            where event.published = false
              and event.deadLettered = false
              and (event.nextAttemptAt is null or event.nextAttemptAt <= :now)
              and not exists (
                  select 1 from OutboxEvent earlier
                  where earlier.aggregateType = event.aggregateType
                    and earlier.aggregateId = event.aggregateId
                    and earlier.published = false
                    and earlier.deadLettered = false
                    and (
                         earlier.occurredAt < event.occurredAt
                         or (earlier.occurredAt = event.occurredAt and earlier.id < event.id)
                    )
              )
            order by event.occurredAt
            """)
    List<UUID> findDueEventIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEvent event where event.id = :eventId")
    Optional<OutboxEvent> findByIdForUpdate(@Param("eventId") UUID eventId);
}
