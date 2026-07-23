package com.fooddelivery.order.infrastructure.repository;

import com.fooddelivery.order.infrastructure.persistence.ConsumedAggregateSequence;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.SpecHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumedAggregateSequenceRepository
        extends JpaRepository<ConsumedAggregateSequence, ConsumedAggregateSequence.ConsumedAggregateSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = SpecHints.HINT_SPEC_LOCK_TIMEOUT, value = "-2"))
    @Query("""
            select cursor from ConsumedAggregateSequence cursor
            where cursor.consumerName = :consumerName
              and cursor.aggregateType = :aggregateType
              and cursor.aggregateId = :aggregateId
            """)
    Optional<ConsumedAggregateSequence> findForUpdate(
            @Param("consumerName") String consumerName,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") UUID aggregateId);

    default Optional<Long> findCurrent(String consumerName, String aggregateType, UUID aggregateId) {
        return findById(new ConsumedAggregateSequence.ConsumedAggregateSequenceId(
                        consumerName, aggregateType, aggregateId))
                .map(ConsumedAggregateSequence::getLastAppliedSequence);
    }
}
