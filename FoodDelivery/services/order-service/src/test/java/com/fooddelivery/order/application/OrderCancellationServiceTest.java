package com.fooddelivery.order.application;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.saga.OrderCompensationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCancellationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private OrderCompensationService compensationService;

    private OrderCancellationService service;

    @BeforeEach
    void setUp() {
        service = new OrderCancellationService(
                orderRepository,
                outboxEventRepository,
                compensationService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void customerCancelsPendingOrderWithoutCompensation() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN);
        UUID orderId = order.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findDetailedById(orderId)).thenReturn(Optional.of(order));

        Order result = service.cancel(orderId, "changed mind", false);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getCancellationCode()).isEqualTo(CancellationCode.CUSTOMER_REQUESTED);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getRefundStatus()).isEqualTo(RefundStatus.NOT_REQUIRED);
        verify(compensationService, never()).start(any(), any(), any(), any());
        verify(outboxEventRepository).saveAll(any());
    }

    @Test
    void customerCancelsPaidOrderStartsCompensation() {
        Order order = paidOrder();
        UUID orderId = order.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findDetailedById(orderId)).thenReturn(Optional.of(order));

        service.cancel(orderId, "no longer needed", false);

        verify(compensationService).start(
                eq(orderId),
                eq(CancellationCode.CUSTOMER_REQUESTED),
                eq("no longer needed"),
                eq(OrderEventPayloads.Source.CUSTOMER));
    }

    @Test
    void adminCancelsUsesAdminCode() {
        Order order = paidOrder();
        UUID orderId = order.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.findDetailedById(orderId)).thenReturn(Optional.of(order));

        service.cancel(orderId, "fraud", true);

        verify(compensationService).start(
                eq(orderId),
                eq(CancellationCode.ADMIN_CANCELLED),
                eq("fraud"),
                eq(OrderEventPayloads.Source.ADMIN));
    }

    @Test
    void preparingIsRejectedForCustomer() {
        Order order = paidOrder();
        order.acceptByRestaurant(UUID.randomUUID());
        order.startPreparing(UUID.randomUUID());
        UUID orderId = order.getId();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // compensation.start will call beginCompensation which throws — we simulate by calling domain
        assertThatThrownBy(() -> order.requestCancellation(
                "too late",
                CancellationCode.CUSTOMER_REQUESTED,
                OrderEventPayloads.Source.CUSTOMER))
                .isInstanceOf(InvalidOrderStateException.class);

        // service still delegates to compensation for non-PENDING
        when(orderRepository.findDetailedById(orderId)).thenReturn(Optional.of(order));
        service.cancel(orderId, "too late", false);
        ArgumentCaptor<CancellationCode> codeCaptor = ArgumentCaptor.forClass(CancellationCode.class);
        verify(compensationService).start(eq(orderId), codeCaptor.capture(), eq("too late"), eq(OrderEventPayloads.Source.CUSTOMER));
        assertThat(codeCaptor.getValue()).isEqualTo(CancellationCode.CUSTOMER_REQUESTED);
    }

    private Order paidOrder() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(50_000));
        order.markPaid(NOW.minusSeconds(60), Duration.ofMinutes(10));
        order.clearPendingOutboxEvents();
        return order;
    }
}
