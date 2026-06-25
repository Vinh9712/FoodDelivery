package com.fooddelivery.restaurant.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuResponse {
    private UUID restaurantId;
    private String restaurantName;
    private List<CategoryWithItems> categories;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryWithItems {
        private UUID id;
        private String name;
        private String description;
        private List<MenuItemSummary> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuItemSummary {
        private UUID id;
        private String name;
        private String description;
        private Double price;
        private Double discountPrice;
        private Boolean isAvailable;
        private Boolean isVegetarian;
        private Boolean isSpicy;
        private Integer preparationTimeMin;
        private String imageUrl;
    }
}