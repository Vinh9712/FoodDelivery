package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.MenuCategoryRequest;
import com.fooddelivery.restaurant.api.dto.MenuCategoryResponse;

import java.util.List;
import java.util.UUID;

public interface MenuCategoryService {
    MenuCategoryResponse createCategory(UUID restaurantId, MenuCategoryRequest request);
    MenuCategoryResponse updateCategory(UUID categoryId, MenuCategoryRequest request);
    void deleteCategory(UUID categoryId);
    List<MenuCategoryResponse> getCategoriesByRestaurant(UUID restaurantId);
    MenuCategoryResponse getCategoryById(UUID categoryId);
}