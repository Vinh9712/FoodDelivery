package com.fooddelivery.order.application.listener;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.client.PaymentServiceClient;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.infrastructure.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DeliveryFailedEventListenerTest {

    @Test
    void terminalAssignmentFailureRefundsAndCancelsOrderOnce() {
        OrderRepository orders = mock(OrderRepository.class);
        ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
        PaymentServiceClient payments = mock(PaymentServiceClient.class);
        OutboxEventRepository outbox = mock(OutboxEventRepository.class);
        DeliveryFailedEventListener listener = new DeliveryFailedEventListener(orders, processed, payments, outbox);
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(65000));
        order.markAsPaid();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        listener.onDeliveryFailed(Map.of(
                "eventId", eventId.toString(),
                "payload", Map.of("orderId", orderId.toString(), "reason", "No available driver")));
        when(processed.existsByEventIdAndConsumer(any(), any())).thenReturn(true);
        listener.onDeliveryFailed(Map.of(
                "eventId", eventId.toString(),
                "payload", Map.of("orderId", orderId.toString(), "reason", "No available driver")));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(payments).refundPayment(any());
        verify(orders).save(order);
        verify(processed).markProcessed(eventId, "order-service-delivery-failed");
        verifyNoMoreInteractions(payments);
    }
}
