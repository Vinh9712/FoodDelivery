package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.MenuResponse;

import java.util.UUID;

public interface MenuService {
    MenuResponse getMenuByRestaurantId(UUID restaurantId);
}