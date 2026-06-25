package com.fooddelivery.restaurant.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantSearchRequest {
    private String name;
    private String city;
    private String district;
    private String status;
    private BigDecimal minRating;
    private Integer page;
    private Integer size;
}