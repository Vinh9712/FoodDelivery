package com.fooddelivery.order.infrastructure.client.dto;

import java.util.UUID;

public record RestaurantOwnershipResponse(UUID restaurantId, UUID userId, boolean owner) {
}
