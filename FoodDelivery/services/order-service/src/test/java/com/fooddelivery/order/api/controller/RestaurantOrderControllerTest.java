package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.dto.RejectOrderRequest;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.application.RestaurantOrderService;
import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.security.RestaurantOrderAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantOrderControllerTest {

    @Mock
    private RestaurantOrderService restaurantOrderService;
    @Mock
    private RestaurantOrderAuthorizationService authorizationService;
    @Mock
    private OrderMapper orderMapper;

    private RestaurantOrderController controller;
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private final UUID ownerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new RestaurantOrderController(
                restaurantOrderService, authorizationService, orderMapper);
    }

    @Test
    void ownerCanAcceptOrder() {
        Order order = stubOrder(OrderStatus.CONFIRMED);
        when(restaurantOrderService.accept(orderId, ownerId)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.CONFIRMED));
        doNothing().when(authorizationService).assertCanManageOrder(eq(orderId), any());

        ResponseEntity<OrderResponse> result = controller.accept(orderId, ownerAuth());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(authorizationService).assertCanManageOrder(eq(orderId), any());
        verify(restaurantOrderService).accept(orderId, ownerId);
    }

    @Test
    void nonOwnerIsHiddenAsNotFound() {
        doThrow(new OrderNotFoundException(orderId))
                .when(authorizationService).assertCanManageOrder(eq(orderId), any());

        assertThatThrownBy(() -> controller.accept(orderId, ownerAuth()))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void adminCanAcceptViaBypassPath() {
        UUID adminId = UUID.randomUUID();
        Order order = stubOrder(OrderStatus.CONFIRMED);
        when(restaurantOrderService.accept(orderId, adminId)).thenReturn(order);
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.CONFIRMED));
        doNothing().when(authorizationService).assertCanManageOrder(eq(orderId), any());

        ResponseEntity<OrderResponse> result = controller.accept(orderId, auth(adminId, "ADMIN"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        verify(restaurantOrderService).accept(orderId, adminId);
    }

    @Test
    void blankRejectReasonFailsValidation() {
        assertThat(validator.validate(new RejectOrderRequest("   "))).isNotEmpty();
        assertThat(validator.validate(new RejectOrderRequest(""))).isNotEmpty();
    }

    @Test
    void missingRejectReasonFailsValidation() {
        assertThat(validator.validate(new RejectOrderRequest(null))).isNotEmpty();
    }

    @Test
    void invalidTransitionIsMappedToConflictByGlobalHandler() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var problem = handler.handleInvalidOrderState(
                new InvalidOrderStateException("Expected status CONFIRMED but was PAID"));
        assertThat(problem.getStatus()).isEqualTo(409);
    }

    @Test
    void invalidTransitionPropagatesAsInvalidOrderState() {
        doNothing().when(authorizationService).assertCanManageOrder(eq(orderId), any());
        when(restaurantOrderService.startPreparing(orderId, ownerId))
                .thenThrow(new InvalidOrderStateException("Expected status CONFIRMED but was PAID"));

        assertThatThrownBy(() -> controller.startPreparing(orderId, ownerAuth()))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void listReturnsPagedOrdersForRestaurant() {
        Order order = stubOrder(OrderStatus.PAID);
        Pageable pageable = PageRequest.of(0, 10);
        when(restaurantOrderService.list(eq(restaurantId), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.PAID));
        doNothing().when(authorizationService).assertCanManageRestaurant(eq(restaurantId), any());

        ResponseEntity<Page<OrderResponse>> result =
                controller.list(restaurantId, null, pageable, ownerAuth());

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).hasSize(1);
        assertThat(result.getBody().getContent().getFirst().status()).isEqualTo(OrderStatus.PAID);
        verify(authorizationService).assertCanManageRestaurant(eq(restaurantId), any());
    }

    @Test
    void listWithStatusFilters() {
        Order order = stubOrder(OrderStatus.CONFIRMED);
        Pageable pageable = PageRequest.of(0, 5);
        when(restaurantOrderService.list(eq(restaurantId), eq(OrderStatus.CONFIRMED), any()))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.CONFIRMED));
        doNothing().when(authorizationService).assertCanManageRestaurant(eq(restaurantId), any());

        ResponseEntity<Page<OrderResponse>> result =
                controller.list(restaurantId, OrderStatus.CONFIRMED, pageable, ownerAuth());

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent().getFirst().status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(restaurantOrderService).list(restaurantId, OrderStatus.CONFIRMED, pageable);
    }

    @Test
    void readyAndRejectSuccessPaths() {
        Order ready = stubOrder(OrderStatus.READY_FOR_PICKUP);
        Order rejected = stubOrder(OrderStatus.CANCELLATION_PENDING);
        when(restaurantOrderService.markReady(orderId, ownerId)).thenReturn(ready);
        when(restaurantOrderService.reject(eq(orderId), eq(ownerId), eq("Kitchen capacity exceeded")))
                .thenReturn(rejected);
        when(orderMapper.toResponse(ready)).thenReturn(response(OrderStatus.READY_FOR_PICKUP));
        when(orderMapper.toResponse(rejected)).thenReturn(response(OrderStatus.CANCELLATION_PENDING));
        doNothing().when(authorizationService).assertCanManageOrder(eq(orderId), any());

        assertThat(controller.markReady(orderId, ownerAuth()).getBody().status())
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(controller.reject(orderId, new RejectOrderRequest("Kitchen capacity exceeded"), ownerAuth())
                .getBody().status())
                .isEqualTo(OrderStatus.CANCELLATION_PENDING);
    }

    private Authentication ownerAuth() {
        return auth(ownerId, "RESTAURANT_OWNER");
    }

    private Authentication auth(UUID subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private Order stubOrder(OrderStatus status) {
        return org.mockito.Mockito.mock(Order.class);
    }

    private OrderResponse response(OrderStatus status) {
        return new OrderResponse(
                orderId,
                UUID.randomUUID(),
                restaurantId,
                status,
                BigDecimal.valueOf(100_000),
                null,
                PaymentStatus.PAID,
                RefundStatus.NOT_REQUIRED,
                Instant.parse("2026-07-22T00:00:00Z"),
                Instant.parse("2026-07-22T00:10:00Z"),
                Instant.parse("2026-07-22T00:00:00Z"),
                Instant.parse("2026-07-22T00:00:00Z"));
    }
}
