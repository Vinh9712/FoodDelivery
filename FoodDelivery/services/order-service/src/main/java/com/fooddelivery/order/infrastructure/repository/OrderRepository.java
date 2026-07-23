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

    /**
     * Detail load: fetch items in one query. Do <strong>not</strong> also entity-graph
     * {@code statusHistory} — Hibernate cannot simultaneously fetch two bags
     * ({@code MultipleBagFetchException}). History loads lazily (OSIV / batch).
     */
    @Query("select distinct o from Order o left join fetch o.items where o.id = :id")
    Optional<Order> findDetailedById(@Param("id") UUID id);

    Optional<Order> findByCustomerIdAndClientRequestId(UUID customerId, String clientRequestId);

    Optional<Order> findByIdAndCustomerId(UUID id, UUID customerId);

    Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    List<Order> findTop100ByStatusOrderByCreatedAtAsc(OrderStatus status);

    Page<Order> findByRestaurantIdAndStatus(UUID restaurantId, OrderStatus status, Pageable pageable);

    Page<Order> findByRestaurantId(UUID restaurantId, Pageable pageable);

    /**
     * Admin/customer order list. Null filters are ignored.
     * <p>Uses SpEL null checks so PostgreSQL does not need to infer types for
     * {@code :param is null} bindings (Instant/UUID null → SQLState 42P18).
     */
    @Query("""
            select o from Order o
            where (:#{#customerId == null} = true or o.customerId = :customerId)
              and (:#{#status == null} = true or o.status = :status)
              and (:#{#restaurantId == null} = true or o.restaurantId = :restaurantId)
              and (:#{#from == null} = true or o.createdAt >= :from)
              and (:#{#to == null} = true or o.createdAt <= :to)
            """)
    Page<Order> findAllFiltered(
            @Param("customerId") UUID customerId,
            @Param("status") OrderStatus status,
            @Param("restaurantId") UUID restaurantId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    /**
     * Order history for a customer (typically terminal statuses).
     */
    @Query("""
            select o from Order o
            where o.customerId = :customerId
              and o.status in :statuses
            """)
    Page<Order> findHistoryByCustomerAndStatusIn(
            @Param("customerId") UUID customerId,
            @Param("statuses") java.util.Collection<OrderStatus> statuses,
            Pageable pageable);

    @Query("""
            select count(o) from Order o
            where o.restaurantId = :restaurantId
              and o.createdAt >= :from
            """)
    long countByRestaurantSince(@Param("restaurantId") UUID restaurantId, @Param("from") Instant from);

    @Query("""
            select coalesce(sum(o.totalAmount), 0) from Order o
            where o.restaurantId = :restaurantId
              and o.status = com.fooddelivery.order.domain.model.valueobject.OrderStatus.DELIVERED
              and o.createdAt >= :from
            """)
    java.math.BigDecimal sumDeliveredRevenueSince(
            @Param("restaurantId") UUID restaurantId, @Param("from") Instant from);

    @Query("""
            select count(o) from Order o
            where o.restaurantId = :restaurantId
              and o.status = :status
              and o.createdAt >= :from
            """)
    long countByRestaurantAndStatusSince(
            @Param("restaurantId") UUID restaurantId,
            @Param("status") OrderStatus status,
            @Param("from") Instant from);

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
