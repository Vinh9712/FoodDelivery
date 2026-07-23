package com.fooddelivery.restaurant.security;

import com.fooddelivery.restaurant.domain.MenuCategoryRepository;
import com.fooddelivery.restaurant.domain.MenuItemRepository;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RestaurantAuthorizationServiceTests {

    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final MenuItemRepository menuItemRepository = mock(MenuItemRepository.class);
    private final MenuCategoryRepository menuCategoryRepository = mock(MenuCategoryRepository.class);
    private final RestaurantAuthorizationService authorization = new RestaurantAuthorizationService(
            restaurantRepository, menuItemRepository, menuCategoryRepository);

    @Test
    void restaurantOwnerCanOnlyManageOwnedResources() {
        UUID ownerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(restaurantRepository.existsByIdAndOwnerId(restaurantId, ownerId)).thenReturn(true);
        when(menuItemRepository.findRestaurantOwnerIdByItemId(itemId)).thenReturn(Optional.of(ownerId));
        when(menuCategoryRepository.findRestaurantOwnerIdByCategoryId(categoryId)).thenReturn(Optional.of(ownerId));

        var authentication = authentication(ownerId, "RESTAURANT_OWNER");

        assertThat(authorization.canManageRestaurant(restaurantId, authentication)).isTrue();
        assertThat(authorization.canManageItem(itemId, authentication)).isTrue();
        assertThat(authorization.canManageCategory(categoryId, authentication)).isTrue();
        assertThat(authorization.canManageRestaurant(restaurantId,
                authentication(UUID.randomUUID(), "RESTAURANT_OWNER"))).isFalse();
    }

    @Test
    void adminCanManageAnyRestaurantResource() {
        var authentication = authentication(UUID.randomUUID(), "ADMIN");

        assertThat(authorization.canManageRestaurant(UUID.randomUUID(), authentication)).isTrue();
        assertThat(authorization.canManageItem(UUID.randomUUID(), authentication)).isTrue();
        assertThat(authorization.canManageCategory(UUID.randomUUID(), authentication)).isTrue();
        verifyNoInteractions(restaurantRepository, menuItemRepository, menuCategoryRepository);
    }

    private UsernamePasswordAuthenticationToken authentication(UUID subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
