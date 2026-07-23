package com.fooddelivery.order.infrastructure.repository;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // ==================== EXISTING METHODS ====================

    @EntityGraph(attributePaths = {"statusHistory"})
    Optional<Order> findWithHistoryById(UUID id);

    Optional<Order> findByCustomerIdAndClientRequestId(UUID customerId, String clientRequestId);

    List<Order> findTop100ByStatusOrderByCreatedAtAsc(OrderStatus status);

    // ==================== NEW METHODS ====================

    /**
     * Get orders by customer ID with pagination
     */
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    /**
     * Get orders by customer ID and status with pagination
     */
    Page<Order> findByCustomerIdAndStatus(UUID customerId, OrderStatus status, Pageable pageable);

    /**
     * Get order history for a customer (all orders sorted by created date DESC)
     */
    List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    /**
     * Get orders by customer ID and status (for history filtering)
     */
    List<Order> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, OrderStatus status);

    /**
     * Check if customer has any order with specific status
     */
    boolean existsByCustomerIdAndStatus(UUID customerId, OrderStatus status);

    /**
     * Count orders by customer and status
     */
    long countByCustomerIdAndStatus(UUID customerId, OrderStatus status);
}