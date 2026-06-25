package com.fooddelivery.restaurant.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemEvent {
    private String eventType;
    private UUID itemId;
    private UUID restaurantId;
    private UUID categoryId;
    private String name;
    private BigDecimal price;
    private Boolean isAvailable;
    private Instant timestamp;
}