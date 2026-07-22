package com.fooddelivery.order.flow;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.infrastructure.client.DeliveryServiceClient;
import com.fooddelivery.order.infrastructure.client.NotificationServiceClient;
import com.fooddelivery.order.infrastructure.client.PaymentServiceClient;
import com.fooddelivery.order.infrastructure.client.RestaurantServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryResponse;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryStatusResponse;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderDeliveryReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Schedule POST commits in delivery-service but response is lost; lookup attaches same delivery, no refund.
 */
@SpringBootTest
@ActiveProfiles("test")
class LostDeliveryResponseIT {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDeliveryReconciliationService reconciliationService;

    @MockBean
    private DeliveryServiceClient deliveryServiceClient;
    @MockBean
    private PaymentServiceClient paymentServiceClient;
    @MockBean
    private NotificationServiceClient notificationServiceClient;
    @MockBean
    private RestaurantServiceClient restaurantServiceClient;

    @Test
    void lostScheduleResponseIsRecoveredByLookupWithoutRefund() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(60_000));
        order.markPaid(Instant.parse("2026-07-22T08:00:00Z"), Duration.ofMinutes(10));
        order.acceptByRestaurant(UUID.randomUUID());
        order.startPreparing(UUID.randomUUID());
        order.markReadyForPickup(UUID.randomUUID());
        order = orderRepository.saveAndFlush(order);
        UUID orderId = order.getId();
        UUID deliveryId = UUID.randomUUID();

        // Lookup finds existing delivery (POST already committed remotely)
        when(deliveryServiceClient.findByOrderId(orderId))
                .thenReturn(new DeliveryStatusResponse(
                        deliveryId,
                        orderId,
                        "FINDING_DRIVER",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        reconciliationService.reconcile(orderId);

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(reloaded.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(paymentServiceClient, never()).refundPayment(anyString(), any());
        // Schedule should not be blindly retried when lookup already attached delivery
        // (implementation may still skip schedule — assert delivery attached is the recovery signal)
    }
}
