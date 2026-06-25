package com.fooddelivery.order.infrastructure.repository;

import com.fooddelivery.order.domain.model.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"statusHistory"})
    Optional<Order> findWithHistoryById(UUID id);
}
