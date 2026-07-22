package com.fooddelivery.delivery.infrastructure.repository;

import com.fooddelivery.delivery.infrastructure.persistence.DeferredIntegrationEvent;
import com.fooddelivery.delivery.infrastructure.persistence.DeferredIntegrationEvent.Status;
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
public interface DeferredIntegrationEventRepository extends JpaRepository<DeferredIntegrationEvent, UUID> {

    boolean existsByConsumerNameAndAggregateTypeAndAggregateIdAndAggregateSequence(
            String consumerName, String aggregateType, UUID aggregateId, long aggregateSequence);

    Optional<DeferredIntegrationEvent> findByConsumerNameAndEventId(String consumerName, UUID eventId);

    Optional<DeferredIntegrationEvent> findByConsumerNameAndAggregateTypeAndAggregateIdAndAggregateSequence(
            String consumerName, String aggregateType, UUID aggregateId, long aggregateSequence);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = SpecHints.HINT_SPEC_LOCK_TIMEOUT, value = "-2"))
    @Query("""
            select event from DeferredIntegrationEvent event
            where event.consumerName = :consumerName
              and event.aggregateType = :aggregateType
              and event.aggregateId = :aggregateId
              and event.aggregateSequence = :sequence
              and event.status = :status
            """)
    Optional<DeferredIntegrationEvent> findWaitingForUpdate(
            @Param("consumerName") String consumerName,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") UUID aggregateId,
            @Param("sequence") long sequence,
            @Param("status") Status status);

    @Query("""
            select event from DeferredIntegrationEvent event
            where event.status = com.fooddelivery.delivery.infrastructure.persistence.DeferredIntegrationEvent.Status.WAITING_FOR_PREDECESSOR
              and event.nextAttemptAt <= :now
            order by event.receivedAt, event.aggregateSequence, event.id
            """)
    List<DeferredIntegrationEvent> findDueWaiting(@Param("now") Instant now, Pageable pageable);

    long countByStatus(Status status);
}
