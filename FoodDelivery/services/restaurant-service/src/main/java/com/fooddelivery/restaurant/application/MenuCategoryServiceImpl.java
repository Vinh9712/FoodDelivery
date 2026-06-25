package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.MenuCategoryRequest;
import com.fooddelivery.restaurant.api.dto.MenuCategoryResponse;
import com.fooddelivery.restaurant.domain.MenuCategory;
import com.fooddelivery.restaurant.domain.MenuCategoryRepository;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.exception.CategoryNotFoundException;
import com.fooddelivery.restaurant.exception.RestaurantNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MenuCategoryServiceImpl implements MenuCategoryService {

    private final MenuCategoryRepository menuCategoryRepository;
    private final RestaurantRepository restaurantRepository;

    @Override
    public MenuCategoryResponse createCategory(UUID restaurantId, MenuCategoryRequest request) {
        log.info("Creating category for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + restaurantId));

        if (menuCategoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, request.getName())) {
            throw new IllegalArgumentException("Category name already exists for this restaurant");
        }

        MenuCategory category = MenuCategory.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        MenuCategory saved = menuCategoryRepository.save(category);
        log.info("Category created with ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    public MenuCategoryResponse updateCategory(UUID categoryId, MenuCategoryRequest request) {
        log.info("Updating category: {}", categoryId);

        MenuCategory category = menuCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        category.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);

        MenuCategory updated = menuCategoryRepository.save(category);
        return mapToResponse(updated);
    }

    @Override
    public void deleteCategory(UUID categoryId) {
        log.info("Deleting category: {}", categoryId);
        if (!menuCategoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException("Category not found: " + categoryId);
        }
        menuCategoryRepository.deleteById(categoryId);
    }

    @Override
    public List<MenuCategoryResponse> getCategoriesByRestaurant(UUID restaurantId) {
        log.info("Getting categories for restaurant: {}", restaurantId);
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RestaurantNotFoundException("Restaurant not found: " + restaurantId);
        }
        return menuCategoryRepository.findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(restaurantId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MenuCategoryResponse getCategoryById(UUID categoryId) {
        MenuCategory category = menuCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + categoryId));
        return mapToResponse(category);
    }

    private MenuCategoryResponse mapToResponse(MenuCategory category) {
        return MenuCategoryResponse.builder()
                .id(category.getId())
                .restaurantId(category.getRestaurant().getId())
                .name(category.getName())
                .description(category.getDescription())
                .displayOrder(category.getDisplayOrder())
                .isActive(category.getIsActive())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}