package com.fooddelivery.restaurant.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
    List<MenuItem> findByRestaurantIdOrderByDisplayOrderAsc(UUID restaurantId);
    List<MenuItem> findByRestaurantIdAndIsAvailableTrueOrderByDisplayOrderAsc(UUID restaurantId);
    List<MenuItem> findByCategoryId(UUID categoryId);

    @Query("select item.restaurant.ownerId from MenuItem item where item.id = :itemId")
    Optional<UUID> findRestaurantOwnerIdByItemId(@Param("itemId") UUID itemId);
}
