package com.fooddelivery.restaurant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantRequest {
    @NotNull
    private UUID ownerId;

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;

    @NotBlank
    @Size(max = 20)
    private String phone;

    @NotBlank
    private String addressLine;

    private String district;

    @NotBlank
    private String city;

    private LocalTime openTime;
    private LocalTime closeTime;

    private BigDecimal minOrderAmount;
    private Integer estimatedDeliveryTimeMin;
    private String logoUrl;
    private String bannerUrl;
}