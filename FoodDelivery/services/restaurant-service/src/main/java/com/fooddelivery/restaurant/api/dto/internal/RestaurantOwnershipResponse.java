package com.fooddelivery.restaurant.api.dto.internal;

import java.util.UUID;

public record RestaurantOwnershipResponse(UUID restaurantId, UUID userId, boolean owner) {
}
