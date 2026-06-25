package com.fooddelivery.restaurant.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<RestaurantReview, UUID> {

    Page<RestaurantReview> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);

    Page<RestaurantReview> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);

    boolean existsByOrderId(UUID orderId);

    boolean existsByRestaurantIdAndOrderId(UUID restaurantId, UUID orderId);

    @Query("SELECT AVG(r.rating) FROM RestaurantReview r WHERE r.restaurant.id = :restaurantId")
    Double calculateAverageRating(@Param("restaurantId") UUID restaurantId);

    @Query("SELECT COUNT(r) FROM RestaurantReview r WHERE r.restaurant.id = :restaurantId")
    Long countReviewsByRestaurantId(@Param("restaurantId") UUID restaurantId);
}