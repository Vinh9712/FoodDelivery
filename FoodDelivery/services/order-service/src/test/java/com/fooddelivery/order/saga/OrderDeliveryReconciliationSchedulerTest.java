package com.fooddelivery.order.saga;

import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDeliveryReconciliationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderDeliveryReconciliationService reconciliationService;

    private OrderDeliveryReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new OrderDeliveryReconciliationScheduler(
                orderRepository,
                reconciliationService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                50);
    }

    @Test
    void claimsDueOrdersAndReconcilesEach() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(orderRepository.findDueDeliveryReconciliationOrderIds(eq(NOW), any()))
                .thenReturn(List.of(first, second));

        scheduler.reconcileDueOrders();

        verify(reconciliationService).reconcile(first);
        verify(reconciliationService).reconcile(second);
    }

    @Test
    void continuesWhenOneOrderFails() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(orderRepository.findDueDeliveryReconciliationOrderIds(eq(NOW), any()))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("boom")).when(reconciliationService).reconcile(first);

        scheduler.reconcileDueOrders();

        verify(reconciliationService).reconcile(first);
        verify(reconciliationService).reconcile(second);
    }
}
