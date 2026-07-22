package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.internal.MenuQuoteRequest;
import com.fooddelivery.restaurant.api.dto.internal.MenuQuoteResponse;
import com.fooddelivery.restaurant.domain.MenuItem;
import com.fooddelivery.restaurant.domain.MenuItemRepository;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.exception.RestaurantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuQuoteService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MenuQuoteResponse quote(UUID restaurantId, MenuQuoteRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant not found: " + restaurantId));
        if (!restaurant.canAcceptOrders(LocalTime.now(clock))) {
            throw new IllegalArgumentException("Restaurant is not accepting orders");
        }

        Map<UUID, Integer> quantities = aggregateQuantities(request.items());
        Map<UUID, MenuItem> menuItems = new LinkedHashMap<>();
        menuItemRepository.findAllById(quantities.keySet())
                .forEach(item -> menuItems.put(item.getId(), item));

        if (menuItems.size() != quantities.size()) {
            throw new IllegalArgumentException("One or more menu items do not exist");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        var quotedItems = new java.util.ArrayList<MenuQuoteResponse.Item>();
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            MenuItem item = menuItems.get(entry.getKey());
            if (!restaurantId.equals(item.getRestaurant().getId())) {
                throw new IllegalArgumentException("All menu items must belong to the selected restaurant");
            }
            if (!Boolean.TRUE.equals(item.getIsAvailable())) {
                throw new IllegalArgumentException("Menu item is unavailable: " + item.getId());
            }

            BigDecimal unitPrice = effectivePrice(item);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(entry.getValue()));
            subtotal = subtotal.add(lineTotal);
            quotedItems.add(new MenuQuoteResponse.Item(
                    item.getId(),
                    item.getName(),
                    item.getDescription(),
                    unitPrice,
                    entry.getValue(),
                    lineTotal));
        }

        BigDecimal minimumOrder = restaurant.getMinOrderAmount() == null
                ? BigDecimal.ZERO
                : restaurant.getMinOrderAmount();
        if (subtotal.compareTo(minimumOrder) < 0) {
            throw new IllegalArgumentException("Order subtotal is below the restaurant minimum");
        }
        return new MenuQuoteResponse(restaurantId, subtotal, pickup(restaurant), List.copyOf(quotedItems));
    }

    private MenuQuoteResponse.PickupSnapshot pickup(Restaurant restaurant) {
        String addressText = java.util.stream.Stream.of(
                        restaurant.getAddressLine(), restaurant.getDistrict(), restaurant.getCity())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        return new MenuQuoteResponse.PickupSnapshot(
                restaurant.getId(), restaurant.getName(), restaurant.getPhone(), addressText, null, null);
    }

    private Map<UUID, Integer> aggregateQuantities(List<MenuQuoteRequest.Item> items) {
        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (MenuQuoteRequest.Item item : items) {
            int quantity = Math.addExact(quantities.getOrDefault(item.menuItemId(), 0), item.quantity());
            if (quantity > 99) {
                throw new IllegalArgumentException("Combined quantity cannot exceed 99 per menu item");
            }
            quantities.put(item.menuItemId(), quantity);
        }
        return quantities;
    }

    private BigDecimal effectivePrice(MenuItem item) {
        BigDecimal price = item.getPrice();
        BigDecimal discountPrice = item.getDiscountPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Menu item has an invalid price: " + item.getId());
        }
        if (discountPrice != null
                && discountPrice.compareTo(BigDecimal.ZERO) > 0
                && discountPrice.compareTo(price) < 0) {
            return discountPrice;
        }
        return price;
    }
}
