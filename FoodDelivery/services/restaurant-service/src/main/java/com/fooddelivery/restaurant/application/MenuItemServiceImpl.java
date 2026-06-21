package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.MenuItemRequest;
import com.fooddelivery.restaurant.api.dto.MenuItemResponse;
import com.fooddelivery.restaurant.domain.MenuCategory;
import com.fooddelivery.restaurant.domain.MenuCategoryRepository;
import com.fooddelivery.restaurant.domain.MenuItem;
import com.fooddelivery.restaurant.domain.MenuItemRepository;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.exception.CategoryNotFoundException;
import com.fooddelivery.restaurant.exception.MenuItemNotFoundException;
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
public class MenuItemServiceImpl implements MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantRepository restaurantRepository;
    private final MenuCategoryRepository menuCategoryRepository;

    @Override
    public MenuItemResponse createMenuItem(UUID restaurantId, MenuItemRequest request) {
        log.info("Creating menu item for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + restaurantId));

        MenuCategory category = null;
        if (request.getCategoryId() != null) {
            category = menuCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + request.getCategoryId()));
        }

        MenuItem item = MenuItem.builder()
                .restaurant(restaurant)
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .discountPrice(request.getDiscountPrice())
                .isAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true)
                .isVegetarian(request.getIsVegetarian() != null ? request.getIsVegetarian() : false)
                .isSpicy(request.getIsSpicy() != null ? request.getIsSpicy() : false)
                .preparationTimeMin(request.getPreparationTimeMin() != null ? request.getPreparationTimeMin() : 15)
                .imageUrl(request.getImageUrl())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .build();

        MenuItem saved = menuItemRepository.save(item);
        log.info("Menu item created with ID: {}", saved.getId());

        return mapToResponse(saved);
    }

    @Override
    public MenuItemResponse updateMenuItem(UUID itemId, MenuItemRequest request) {
        log.info("Updating menu item: {}", itemId);

        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new MenuItemNotFoundException("Menu item not found: " + itemId));

        if (request.getCategoryId() != null) {
            MenuCategory category = menuCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new CategoryNotFoundException("Category not found: " + request.getCategoryId()));
            item.setCategory(category);
        }

        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setPrice(request.getPrice());
        item.setDiscountPrice(request.getDiscountPrice());
        item.setIsAvailable(request.getIsAvailable() != null ? request.getIsAvailable() : true);
        item.setIsVegetarian(request.getIsVegetarian() != null ? request.getIsVegetarian() : false);
        item.setIsSpicy(request.getIsSpicy() != null ? request.getIsSpicy() : false);
        item.setPreparationTimeMin(request.getPreparationTimeMin() != null ? request.getPreparationTimeMin() : 15);
        item.setImageUrl(request.getImageUrl());
        item.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);

        MenuItem updated = menuItemRepository.save(item);
        return mapToResponse(updated);
    }

    @Override
    public void deleteMenuItem(UUID itemId) {
        log.info("Deleting menu item: {}", itemId);
        if (!menuItemRepository.existsById(itemId)) {
            throw new MenuItemNotFoundException("Menu item not found: " + itemId);
        }
        menuItemRepository.deleteById(itemId);
    }

    @Override
    public MenuItemResponse getMenuItemById(UUID itemId) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new MenuItemNotFoundException("Menu item not found: " + itemId));
        return mapToResponse(item);
    }

    @Override
    public List<MenuItemResponse> getMenuItemsByRestaurant(UUID restaurantId) {
        log.info("Getting menu items for restaurant: {}", restaurantId);
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RestaurantNotFoundException("Restaurant not found: " + restaurantId);
        }
        return menuItemRepository.findByRestaurantIdOrderByDisplayOrderAsc(restaurantId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public MenuItemResponse updateAvailability(UUID itemId, Boolean isAvailable) {
        log.info("Updating availability for item: {} to {}", itemId, isAvailable);

        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(() -> new MenuItemNotFoundException("Menu item not found: " + itemId));

        item.setIsAvailable(isAvailable);
        MenuItem updated = menuItemRepository.save(item);
        return mapToResponse(updated);
    }

    private MenuItemResponse mapToResponse(MenuItem item) {
        return MenuItemResponse.builder()
                .id(item.getId())
                .restaurantId(item.getRestaurant().getId())
                .categoryId(item.getCategory() != null ? item.getCategory().getId() : null)
                .categoryName(item.getCategory() != null ? item.getCategory().getName() : null)
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .discountPrice(item.getDiscountPrice())
                .isAvailable(item.getIsAvailable())
                .isVegetarian(item.getIsVegetarian())
                .isSpicy(item.getIsSpicy())
                .preparationTimeMin(item.getPreparationTimeMin())
                .imageUrl(item.getImageUrl())
                .displayOrder(item.getDisplayOrder())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}