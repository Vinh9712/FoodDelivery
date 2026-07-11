package com.fooddelivery.restaurant.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    List<Restaurant> findByOwnerId(UUID ownerId);
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
    @Query("SELECT r FROM Restaurant r WHERE " +
            "(:name IS NULL OR LOWER(CAST(r.name AS string)) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%'))) AND " +
            "(:city IS NULL OR LOWER(CAST(r.city AS string)) LIKE LOWER(CONCAT('%', CAST(:city AS string), '%'))) AND " +
            "(:district IS NULL OR LOWER(CAST(r.district AS string)) LIKE LOWER(CONCAT('%', CAST(:district AS string), '%'))) AND " +
            "(:status IS NULL OR r.status = :status) AND " +
            "(:minRating IS NULL OR r.avgRating >= :minRating)")
    Page<Restaurant> searchRestaurants(
            @Param("name") String name,
            @Param("city") String city,
            @Param("district") String district,
            @Param("status") String status,
            @Param("minRating") BigDecimal minRating,
            Pageable pageable
    );
}
