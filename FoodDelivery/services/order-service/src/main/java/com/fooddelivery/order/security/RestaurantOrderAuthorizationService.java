package com.fooddelivery.order.security;

import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.infrastructure.client.RestaurantServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.RestaurantOwnershipResponse;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantOrderAuthorizationService {

    private final OrderRepository orderRepository;
    private final RestaurantServiceClient restaurantServiceClient;

    public void assertCanManageOrder(UUID orderId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        assertOwner(order.getRestaurantId(), authentication, orderId);
    }

    public void assertCanManageRestaurant(UUID restaurantId, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        assertOwner(restaurantId, authentication, restaurantId);
    }

    private void assertOwner(UUID restaurantId, Authentication authentication, UUID notFoundId) {
        UUID userId = UUID.fromString(authentication.getName());
        RestaurantOwnershipResponse ownership = restaurantServiceClient.ownership(restaurantId, userId);
        if (ownership == null || !ownership.owner()) {
            throw new OrderNotFoundException(notFoundId);
        }
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
