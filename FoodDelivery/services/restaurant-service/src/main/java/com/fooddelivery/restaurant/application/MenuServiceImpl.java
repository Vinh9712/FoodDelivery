package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.MenuResponse;
import com.fooddelivery.restaurant.domain.MenuCategory;
import com.fooddelivery.restaurant.domain.MenuCategoryRepository;
import com.fooddelivery.restaurant.domain.MenuItem;
import com.fooddelivery.restaurant.domain.MenuItemRepository;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
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
@Transactional(readOnly = true)
public class MenuServiceImpl implements MenuService {

    private final RestaurantRepository restaurantRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;

    @Override
    public MenuResponse getMenuByRestaurantId(UUID restaurantId) {
        log.info("Getting menu for restaurant: {}", restaurantId);

        // 1. Kiểm tra restaurant tồn tại
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found: " + restaurantId));

        // 2. Lấy danh sách categories
        List<MenuCategory> categories = menuCategoryRepository
                .findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(restaurantId);

        // 3. Lấy danh sách items theo từng category
        List<MenuResponse.CategoryWithItems> categoryWithItems = categories.stream()
                .map(category -> {
                    List<MenuItem> items = menuItemRepository
                            .findByCategoryId(category.getId());

                    List<MenuResponse.MenuItemSummary> itemSummaries = items.stream()
                            .map(this::mapToMenuItemSummary)
                            .collect(Collectors.toList());

                    return MenuResponse.CategoryWithItems.builder()
                            .id(category.getId())
                            .name(category.getName())
                            .description(category.getDescription())
                            .items(itemSummaries)
                            .build();
                })
                .collect(Collectors.toList());

        // 4. Build response
        return MenuResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .categories(categoryWithItems)
                .build();
    }

    private MenuResponse.MenuItemSummary mapToMenuItemSummary(MenuItem item) {
        return MenuResponse.MenuItemSummary.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice().doubleValue())
                .discountPrice(item.getDiscountPrice() != null ? item.getDiscountPrice().doubleValue() : null)
                .isAvailable(item.getIsAvailable())
                .isVegetarian(item.getIsVegetarian())
                .isSpicy(item.getIsSpicy())
                .preparationTimeMin(item.getPreparationTimeMin())
                .imageUrl(item.getImageUrl())
                .build();
    }
}