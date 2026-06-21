package com.fooddelivery.restaurant.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {
    List<MenuCategory> findByRestaurantIdOrderByDisplayOrderAsc(UUID restaurantId);
    List<MenuCategory> findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID restaurantId);
    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);
}