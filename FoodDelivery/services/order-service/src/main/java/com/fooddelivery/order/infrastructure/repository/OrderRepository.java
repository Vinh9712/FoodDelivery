package com.fooddelivery.order.infrastructure.repository;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"statusHistory"})
    Optional<Order> findWithHistoryById(UUID id);

    Optional<Order> findByCustomerIdAndClientRequestId(UUID customerId, String clientRequestId);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    List<Order> findTop100ByStatusOrderByCreatedAtAsc(OrderStatus status);

    Page<Order> findByRestaurantIdAndStatus(UUID restaurantId, OrderStatus status, Pageable pageable);

    Page<Order> findByRestaurantId(UUID restaurantId, Pageable pageable);

    /**
     * READY_FOR_PICKUP orders due for delivery reconciliation.
     * {@code next_delivery_schedule_attempt_at IS NULL} is immediately due (legacy / first try).
     */
    @Query("""
            select o.id from Order o
            where o.status = com.fooddelivery.order.domain.model.valueobject.OrderStatus.READY_FOR_PICKUP
              and (o.nextDeliveryScheduleAttemptAt is null or o.nextDeliveryScheduleAttemptAt <= :now)
            order by o.nextDeliveryScheduleAttemptAt asc nulls first, o.id asc
            """)
    List<UUID> findDueDeliveryReconciliationOrderIds(@Param("now") Instant now, Pageable pageable);

    /**
     * CANCELLATION_PENDING + refund PENDING rows due for refund reconciliation.
     */
    @Query("""
            select o.id from Order o
            where o.status = com.fooddelivery.order.domain.model.valueobject.OrderStatus.CANCELLATION_PENDING
              and o.refundStatus = com.fooddelivery.order.domain.model.valueobject.RefundStatus.PENDING
              and (o.nextRefundAttemptAt is null or o.nextRefundAttemptAt <= :now)
            order by o.nextRefundAttemptAt asc nulls first, o.id asc
            """)
    List<UUID> findDueRefundReconciliationOrderIds(@Param("now") Instant now, Pageable pageable);

    /**
     * PAID orders whose restaurant acceptance deadline has elapsed (deadline &lt;= now).
     */
    @Query("""
            select o.id from Order o
            where o.status = com.fooddelivery.order.domain.model.valueobject.OrderStatus.PAID
              and o.restaurantResponseDeadline is not null
              and o.restaurantResponseDeadline <= :now
            order by o.restaurantResponseDeadline asc, o.id asc
            """)
    List<UUID> findOverdueRestaurantAcceptanceOrderIds(@Param("now") Instant now, Pageable pageable);

    /**
     * Count of PAID orders past restaurant acceptance deadline (overdue gauge).
     */
    @Query("""
            select count(o) from Order o
            where o.status = com.fooddelivery.order.domain.model.valueobject.OrderStatus.PAID
              and o.restaurantResponseDeadline is not null
              and o.restaurantResponseDeadline <= :now
            """)
    long countOverdueRestaurantAcceptance(@Param("now") Instant now);
}
