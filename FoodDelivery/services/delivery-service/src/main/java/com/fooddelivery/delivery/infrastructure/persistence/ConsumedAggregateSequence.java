package com.fooddelivery.delivery.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-consumer last-applied aggregate sequence cursor for ordered inbox processing.
 */
@Entity
@Table(name = "consumed_aggregate_sequences")
@IdClass(ConsumedAggregateSequence.ConsumedAggregateSequenceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedAggregateSequence {

    @Id
    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @Id
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Id
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "last_applied_sequence", nullable = false)
    private long lastAppliedSequence;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ConsumedAggregateSequence(
            String consumerName,
            String aggregateType,
            UUID aggregateId,
            long lastAppliedSequence,
            Instant updatedAt) {
        this.consumerName = consumerName;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.lastAppliedSequence = lastAppliedSequence;
        this.updatedAt = updatedAt;
    }

    public void advanceTo(long sequence, Instant now) {
        if (sequence < this.lastAppliedSequence) {
            throw new IllegalArgumentException(
                    "Cannot rewind aggregate sequence from " + lastAppliedSequence + " to " + sequence);
        }
        this.lastAppliedSequence = sequence;
        this.updatedAt = now;
    }

    @Getter
    @NoArgsConstructor
    public static class ConsumedAggregateSequenceId implements Serializable {
        private String consumerName;
        private String aggregateType;
        private UUID aggregateId;

        public ConsumedAggregateSequenceId(String consumerName, String aggregateType, UUID aggregateId) {
            this.consumerName = consumerName;
            this.aggregateType = aggregateType;
            this.aggregateId = aggregateId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ConsumedAggregateSequenceId that)) {
                return false;
            }
            return Objects.equals(consumerName, that.consumerName)
                    && Objects.equals(aggregateType, that.aggregateType)
                    && Objects.equals(aggregateId, that.aggregateId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(consumerName, aggregateType, aggregateId);
        }
    }
}
