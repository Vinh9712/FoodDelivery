package com.fooddelivery.restaurant.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantResponse {
    private UUID id;
    private UUID ownerId;
    private String name;
    private String description;
    private String phone;
    private String addressLine;
    private String district;
    private String city;
    private String status;
    private LocalTime openTime;
    private LocalTime closeTime;
    private BigDecimal avgRating;
    private Integer totalReviews;
    private BigDecimal minOrderAmount;
    private Integer estimatedDeliveryTimeMin;
    private String logoUrl;
    private String bannerUrl;
    private Boolean isAcceptingOrders;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}