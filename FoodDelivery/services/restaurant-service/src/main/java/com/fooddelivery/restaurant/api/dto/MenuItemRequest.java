package com.fooddelivery.restaurant.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemRequest {
    @NotBlank
    private String name;

    private String description;

    @NotNull
    @Min(0)
    private BigDecimal price;

    private BigDecimal discountPrice;

    private UUID categoryId;

    private Boolean isAvailable;

    private Boolean isVegetarian;

    private Boolean isSpicy;

    private Integer preparationTimeMin;

    private String imageUrl;

    private Integer displayOrder;
}