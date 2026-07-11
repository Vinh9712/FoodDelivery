package com.fooddelivery.restaurant.security;

import com.fooddelivery.restaurant.domain.MenuCategoryRepository;
import com.fooddelivery.restaurant.domain.MenuItemRepository;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("restaurantAuthorization")
@RequiredArgsConstructor
public class RestaurantAuthorizationService {

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuCategoryRepository menuCategoryRepository;

    public boolean canManageRestaurant(UUID restaurantId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        UUID ownerId = ownerId(authentication);
        return ownerId != null && hasRole(authentication, "RESTAURANT_OWNER")
                && restaurantRepository.existsByIdAndOwnerId(restaurantId, ownerId);
    }

    public boolean canManageItem(UUID itemId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        UUID ownerId = ownerId(authentication);
        return ownerId != null && hasRole(authentication, "RESTAURANT_OWNER")
                && menuItemRepository.findRestaurantOwnerIdByItemId(itemId)
                .map(ownerId::equals)
                .orElse(false);
    }

    public boolean canManageCategory(UUID categoryId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return true;
        }
        UUID ownerId = ownerId(authentication);
        return ownerId != null && hasRole(authentication, "RESTAURANT_OWNER")
                && menuCategoryRepository.findRestaurantOwnerIdByCategoryId(categoryId)
                .map(ownerId::equals)
                .orElse(false);
    }

    private boolean isAdmin(Authentication authentication) {
        return hasRole(authentication, "ADMIN");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    private UUID ownerId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
