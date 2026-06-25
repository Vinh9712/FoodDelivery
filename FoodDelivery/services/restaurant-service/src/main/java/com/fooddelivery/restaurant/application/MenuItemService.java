package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.MenuItemRequest;
import com.fooddelivery.restaurant.api.dto.MenuItemResponse;

import java.util.List;
import java.util.UUID;

public interface MenuItemService {
    MenuItemResponse createMenuItem(UUID restaurantId, MenuItemRequest request);
    MenuItemResponse updateMenuItem(UUID itemId, MenuItemRequest request);
    void deleteMenuItem(UUID itemId);
    MenuItemResponse getMenuItemById(UUID itemId);
    List<MenuItemResponse> getMenuItemsByRestaurant(UUID restaurantId);
    MenuItemResponse updateAvailability(UUID itemId, Boolean isAvailable);
}