package com.fooddelivery.restaurant.api.dto;

import jakarta.validation.constraints.NotNull;

public record RestaurantAvailabilityRequest(@NotNull Boolean accepting) {
}
