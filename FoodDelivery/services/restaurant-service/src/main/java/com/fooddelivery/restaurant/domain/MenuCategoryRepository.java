package com.fooddelivery.restaurant.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {
    List<MenuCategory> findByRestaurantIdOrderByDisplayOrderAsc(UUID restaurantId);
    List<MenuCategory> findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(UUID restaurantId);
    boolean existsByRestaurantIdAndNameIgnoreCase(UUID restaurantId, String name);

    @Query("select category.restaurant.ownerId from MenuCategory category where category.id = :categoryId")
    Optional<UUID> findRestaurantOwnerIdByCategoryId(@Param("categoryId") UUID categoryId);
}
