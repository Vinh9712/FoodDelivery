package com.fooddelivery.restaurant.infrastructure.repository;

import com.fooddelivery.restaurant.domain.model.RestaurantReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RestaurantReviewRepository extends JpaRepository<RestaurantReview, UUID> {
    boolean existsByOrderId(UUID orderId);
    Page<RestaurantReview> findByRestaurantId(UUID restaurantId, Pageable pageable);
}
