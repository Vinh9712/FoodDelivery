package com.fooddelivery.restaurant.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantEvent {
    private String eventType;
    private UUID restaurantId;
    private UUID ownerId;
    private String name;
    private String city;
    private String status;
    private Instant timestamp;
}