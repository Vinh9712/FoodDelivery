package com.fooddelivery.delivery.infrastructure.repository;

import com.fooddelivery.delivery.domain.model.DriverReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DriverReviewRepository extends JpaRepository<DriverReview, UUID> {
    boolean existsByOrderId(UUID orderId);
    Page<DriverReview> findByDriverId(UUID driverId, Pageable pageable);
}
