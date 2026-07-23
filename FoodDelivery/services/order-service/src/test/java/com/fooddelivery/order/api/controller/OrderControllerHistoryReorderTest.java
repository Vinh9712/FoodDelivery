package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.application.OrderCancellationService;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderItem;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderSagaOrchestrator;
import com.fooddelivery.order.security.OrderAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerHistoryReorderTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderSagaOrchestrator sagaOrchestrator;
    @Mock
    private OrderAuthorizationService orderAuthorization;
    @Mock
    private OrderCancellationService orderCancellationService;
    @Mock
    private Clock clock;

    private OrderController controller;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new OrderController(
                orderRepository, orderMapper, sagaOrchestrator,
                orderAuthorization, orderCancellationService, clock);
    }

    @Test
    void historyDefaultsToDeliveredAndCancelled() {
        Order order = mock(Order.class);
        Pageable pageable = PageRequest.of(0, 20);
        Authentication auth = auth(customerId, "CUSTOMER");
        when(orderAuthorization.resolveHistoryCustomerId(customerId, auth)).thenReturn(customerId);
        when(orderRepository.findHistoryByCustomerAndStatusIn(eq(customerId), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.DELIVERED));

        ResponseEntity<Page<OrderResponse>> result =
                controller.history(customerId, null, pageable, auth);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).hasSize(1);

        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository).findHistoryByCustomerAndStatusIn(eq(customerId), statuses.capture(), eq(pageable));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                OrderStatus.DELIVERED, OrderStatus.CANCELLED);
    }

    @Test
    void historyRespectsStatusFilter() {
        Order order = mock(Order.class);
        Pageable pageable = PageRequest.of(0, 10);
        Authentication auth = auth(customerId, "CUSTOMER");
        when(orderAuthorization.resolveHistoryCustomerId(customerId, auth)).thenReturn(customerId);
        when(orderRepository.findHistoryByCustomerAndStatusIn(eq(customerId), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.CANCELLED));

        controller.history(customerId, OrderStatus.CANCELLED, pageable, auth);

        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository).findHistoryByCustomerAndStatusIn(eq(customerId), statuses.capture(), eq(pageable));
        assertThat(statuses.getValue()).containsExactly(OrderStatus.CANCELLED);
    }

    @Test
    void reorderPlacesNewOrderFromSource() {
        Order source = mock(Order.class);
        OrderItem item = mock(OrderItem.class);
        Order created = mock(Order.class);
        Authentication auth = auth(customerId, "CUSTOMER");

        when(orderRepository.findDetailedById(orderId)).thenReturn(Optional.of(source));
        when(source.getCustomerId()).thenReturn(customerId);
        when(source.getRestaurantId()).thenReturn(restaurantId);
        when(source.getDeliveryAddressJson()).thenReturn("12 Nguyen Hue, Q1");
        when(source.getNote()).thenReturn("extra chili");
        when(source.getItems()).thenReturn(List.of(item));
        when(item.getMenuItemId()).thenReturn(menuItemId);
        when(item.getQuantity()).thenReturn(2);
        when(sagaOrchestrator.placeOrder(
                eq(customerId), eq(restaurantId), eq("12 Nguyen Hue, Q1"),
                any(), any(), eq("extra chili")))
                .thenReturn(created);
        when(orderMapper.toResponse(created)).thenReturn(response(OrderStatus.PENDING));

        ResponseEntity<OrderResponse> result = controller.reorder(orderId, "idem-reorder-1", auth);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().status()).isEqualTo(OrderStatus.PENDING);
        verify(sagaOrchestrator).placeOrder(
                eq(customerId),
                eq(restaurantId),
                eq("12 Nguyen Hue, Q1"),
                eq("idem-reorder-1"),
                any(),
                eq("extra chili"));
    }

    private Authentication auth(UUID subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private OrderResponse response(OrderStatus status) {
        return new OrderResponse(
                orderId,
                customerId,
                restaurantId,
                status,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(100_000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                List.of(),
                null,
                null,
                PaymentStatus.PAID,
                RefundStatus.NOT_REQUIRED,
                null,
                null,
                List.of(),
                Instant.parse("2026-07-22T00:00:00Z"),
                Instant.parse("2026-07-22T00:10:00Z"),
                Instant.parse("2026-07-22T00:00:00Z"),
                Instant.parse("2026-07-22T00:00:00Z"));
    }
}
