package com.fooddelivery.order.security;

import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.infrastructure.client.RestaurantServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.RestaurantOwnershipResponse;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantOrderAuthorizationServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RestaurantServiceClient restaurantServiceClient;

    private RestaurantOrderAuthorizationService authorization;

    @BeforeEach
    void setUp() {
        authorization = new RestaurantOrderAuthorizationService(orderRepository, restaurantServiceClient);
    }

    @Test
    void ownerOfRestaurantCanManageOrder() {
        UUID ownerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        Order order = new Order(UUID.randomUUID(), restaurantId, BigDecimal.TEN);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(restaurantServiceClient.ownership(restaurantId, ownerId))
                .thenReturn(new RestaurantOwnershipResponse(restaurantId, ownerId, true));

        assertThatCode(() -> authorization.assertCanManageOrder(
                order.getId(), authentication(ownerId, "RESTAURANT_OWNER")))
                .doesNotThrowAnyException();

        verify(restaurantServiceClient).ownership(restaurantId, ownerId);
    }

    @Test
    void nonOwnerIsHiddenAsOrderNotFound() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        Order order = new Order(UUID.randomUUID(), restaurantId, BigDecimal.TEN);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(restaurantServiceClient.ownership(restaurantId, otherUser))
                .thenReturn(new RestaurantOwnershipResponse(restaurantId, otherUser, false));

        assertThatThrownBy(() -> authorization.assertCanManageOrder(
                order.getId(), authentication(otherUser, "RESTAURANT_OWNER")))
                .isInstanceOf(OrderNotFoundException.class);

        verify(restaurantServiceClient).ownership(restaurantId, otherUser);
    }

    @Test
    void adminBypassesOwnershipFeignCheck() {
        UUID orderId = UUID.randomUUID();

        assertThatCode(() -> authorization.assertCanManageOrder(
                orderId, authentication(UUID.randomUUID(), "ADMIN")))
                .doesNotThrowAnyException();

        verifyNoInteractions(restaurantServiceClient);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void missingOrderIsNotFoundForOwnerRole() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorization.assertCanManageOrder(
                orderId, authentication(UUID.randomUUID(), "RESTAURANT_OWNER")))
                .isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(restaurantServiceClient);
    }

    @Test
    void ownerCanManageRestaurantListScope() {
        UUID ownerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(restaurantServiceClient.ownership(restaurantId, ownerId))
                .thenReturn(new RestaurantOwnershipResponse(restaurantId, ownerId, true));

        assertThatCode(() -> authorization.assertCanManageRestaurant(
                restaurantId, authentication(ownerId, "RESTAURANT_OWNER")))
                .doesNotThrowAnyException();
    }

    @Test
    void nonOwnerRestaurantListIsHiddenAsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(restaurantServiceClient.ownership(restaurantId, userId))
                .thenReturn(new RestaurantOwnershipResponse(restaurantId, userId, false));

        assertThatThrownBy(() -> authorization.assertCanManageRestaurant(
                restaurantId, authentication(userId, "RESTAURANT_OWNER")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void adminBypassesRestaurantOwnershipCheck() {
        assertThatCode(() -> authorization.assertCanManageRestaurant(
                UUID.randomUUID(), authentication(UUID.randomUUID(), "ADMIN")))
                .doesNotThrowAnyException();

        verifyNoInteractions(restaurantServiceClient);
    }

    private UsernamePasswordAuthenticationToken authentication(UUID subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
