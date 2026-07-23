package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.mapper.OrderMapper;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderSagaOrchestrator;
import com.fooddelivery.order.security.OrderAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerListTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderSagaOrchestrator sagaOrchestrator;
    @Mock
    private OrderAuthorizationService orderAuthorization;

    private OrderController controller;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();

    @Mock
    private com.fooddelivery.order.application.OrderCancellationService orderCancellationService;

    @BeforeEach
    void setUp() {
        controller = new OrderController(
                orderRepository, orderMapper, sagaOrchestrator, orderAuthorization, orderCancellationService);
    }

    @Test
    void adminListsAllOrdersWithoutUserId() {
        Order order = org.mockito.Mockito.mock(Order.class);
        Pageable pageable = PageRequest.of(0, 20);
        when(orderAuthorization.resolveListCustomerFilter(isNull(), any())).thenReturn(null);
        when(orderRepository.findAllFiltered(isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.PAID));

        ResponseEntity<Page<OrderResponse>> result =
                controller.listOrders(null, null, pageable, auth(UUID.randomUUID(), "ADMIN"));

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).hasSize(1);
        verify(orderRepository).findAllFiltered(null, null, pageable);
    }

    @Test
    void adminListsFilteredByUserIdAndStatus() {
        Order order = org.mockito.Mockito.mock(Order.class);
        Pageable pageable = PageRequest.of(0, 10);
        when(orderAuthorization.resolveListCustomerFilter(eq(customerId), any())).thenReturn(customerId);
        when(orderRepository.findAllFiltered(eq(customerId), eq(OrderStatus.DELIVERED), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.DELIVERED));

        ResponseEntity<Page<OrderResponse>> result =
                controller.listOrders(customerId, OrderStatus.DELIVERED, pageable, auth(UUID.randomUUID(), "ADMIN"));

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent().getFirst().status()).isEqualTo(OrderStatus.DELIVERED);
        verify(orderRepository).findAllFiltered(customerId, OrderStatus.DELIVERED, pageable);
    }

    @Test
    void customerListsOwnOrders() {
        Order order = org.mockito.Mockito.mock(Order.class);
        Pageable pageable = PageRequest.of(0, 20);
        Authentication customerAuth = auth(customerId, "CUSTOMER");
        when(orderAuthorization.resolveListCustomerFilter(eq(customerId), eq(customerAuth))).thenReturn(customerId);
        when(orderRepository.findAllFiltered(eq(customerId), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(orderMapper.toResponse(order)).thenReturn(response(OrderStatus.CONFIRMED));

        ResponseEntity<Page<OrderResponse>> result =
                controller.listOrders(customerId, null, pageable, customerAuth);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).hasSize(1);
        verify(orderAuthorization).resolveListCustomerFilter(customerId, customerAuth);
        verify(orderRepository).findAllFiltered(customerId, null, pageable);
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
