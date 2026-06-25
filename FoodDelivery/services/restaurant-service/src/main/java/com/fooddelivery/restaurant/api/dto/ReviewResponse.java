package com.fooddelivery.restaurant.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {
    private UUID id;
    private UUID restaurantId;
    private String restaurantName;
    private UUID customerId;
    private UUID orderId;
    private Integer rating;
    private String comment;
    private Boolean isVerifiedPurchase;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}