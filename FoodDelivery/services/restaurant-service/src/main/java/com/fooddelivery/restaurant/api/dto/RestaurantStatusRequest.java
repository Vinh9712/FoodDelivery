package com.fooddelivery.restaurant.api.dto;

import com.fooddelivery.restaurant.domain.RestaurantStatus;
import jakarta.validation.constraints.NotNull;

public record RestaurantStatusRequest(@NotNull RestaurantStatus status) {
}
