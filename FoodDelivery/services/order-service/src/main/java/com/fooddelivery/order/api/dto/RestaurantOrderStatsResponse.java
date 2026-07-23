package com.fooddelivery.order.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RestaurantOrderStatsResponse(
        UUID restaurantId,
        Instant since,
        long totalOrders,
        long deliveredOrders,
        long cancelledOrders,
        long activeOrders,
        BigDecimal deliveredRevenue
) {
}
