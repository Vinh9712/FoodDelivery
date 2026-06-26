package com.fooddelivery.order.infrastructure.repository;

import com.fooddelivery.order.domain.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository cho Outbox Events.
 * Hỗ trợ Outbox Poller query các event chưa được publish.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Tìm tất cả event chưa được publish, sắp xếp theo thời gian tạo.
     */
    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc();

    /**
     * Tìm tất cả event theo aggregate.
     */
    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, UUID aggregateId);
}
