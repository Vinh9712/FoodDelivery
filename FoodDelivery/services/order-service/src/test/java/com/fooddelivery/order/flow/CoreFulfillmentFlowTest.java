package com.fooddelivery.order.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.order.api.controller.InternalOrderController;
import com.fooddelivery.order.application.RestaurantOrderService;
import com.fooddelivery.order.application.listener.DeliveryLifecycleEventListener;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.client.DeliveryServiceClient;
import com.fooddelivery.order.infrastructure.client.NotificationServiceClient;
import com.fooddelivery.order.infrastructure.client.PaymentServiceClient;
import com.fooddelivery.order.infrastructure.client.RestaurantServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryResponse;
import com.fooddelivery.order.infrastructure.client.dto.MenuQuoteRequest;
import com.fooddelivery.order.infrastructure.client.dto.MenuQuoteResponse;
import com.fooddelivery.order.infrastructure.client.dto.PaymentRequest;
import com.fooddelivery.order.infrastructure.client.dto.PaymentResponse;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.saga.OrderSagaOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vertical happy-path verification of the core fulfillment flow:
 * PENDING → PAID → CONFIRMED → PREPARING → READY_FOR_PICKUP
 * → (DriverAssigned) → PICKED_UP → DELIVERING → DELIVERED.
 *
 * Uses mocked Feign clients and real order repository/outbox + lifecycle listener.
 */
@SpringBootTest
@ActiveProfiles("test")
class CoreFulfillmentFlowTest {

    @Autowired
    private OrderSagaOrchestrator sagaOrchestrator;

    @Autowired
    private RestaurantOrderService restaurantOrders;

    @Autowired
    private DeliveryLifecycleEventListener deliveryLifecycleEventListener;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InternalOrderController internalOrderController;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RestaurantServiceClient restaurantServiceClient;

    @MockBean
    private PaymentServiceClient paymentServiceClient;

    @MockBean
    private DeliveryServiceClient deliveryServiceClient;

    @MockBean
    private NotificationServiceClient notificationServiceClient;

    private final UUID customerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID menuItemId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();
    private final UUID driverId = UUID.randomUUID();

    @BeforeEach
    void stubCollaborators() {
        when(restaurantServiceClient.quoteMenu(eq(restaurantId), any(MenuQuoteRequest.class)))
                .thenReturn(new MenuQuoteResponse(
                        restaurantId,
                        new BigDecimal("50000"),
                        new MenuQuoteResponse.PickupSnapshot(
                                restaurantId,
                                "Demo Restaurant",
                                "0901000000",
                                "12 Le Loi, District 1",
                                null,
                                null),
                        List.of(new MenuQuoteResponse.Item(
                                menuItemId,
                                "Phở Bò",
                                "Classic beef pho",
                                new BigDecimal("50000"),
                                1,
                                new BigDecimal("50000")))));

        when(paymentServiceClient.processPayment(anyString(), any(PaymentRequest.class)))
                .thenAnswer(invocation -> {
                    PaymentRequest request = invocation.getArgument(1);
                    return new PaymentResponse(
                            request.orderId(),
                            "SUCCESS",
                            "TXN-CORE-FLOW",
                            "Payment successful");
                });

        when(deliveryServiceClient.schedule(anyString(), any(DeliveryRequest.class)))
                .thenAnswer(invocation -> {
                    DeliveryRequest request = invocation.getArgument(1);
                    return new DeliveryResponse(
                            deliveryId,
                            request.orderId(),
                            "PENDING_ASSIGNMENT",
                            null,
                            "Delivery scheduled");
                });
    }

    @Test
    @DisplayName("Core vertical: place → kitchen → delivery lifecycle → DELIVERED + review eligibility")
    void coreFulfillmentFlow_reachesDeliveredAndReviewEligible() {
        Order placed = placeOrder();
        assertThat(placed.getStatus()).isEqualTo(OrderStatus.PAID);
        UUID orderId = placed.getId();

        assertThat(restaurantOrders.accept(orderId, ownerId).getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(restaurantOrders.startPreparing(orderId, ownerId).getStatus())
                .isEqualTo(OrderStatus.PREPARING);
        assertThat(restaurantOrders.markReady(orderId, ownerId).getStatus())
                .isEqualTo(OrderStatus.READY_FOR_PICKUP);

        // After-commit coordinator schedules delivery (mocked Feign)
        verify(deliveryServiceClient, atLeastOnce())
                .schedule(eq("delivery-schedule:" + orderId), any(DeliveryRequest.class));

        publishPickedUpInTransitAndCompleted(orderId);

        Order delivered = orderRepository.findById(orderId).orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.getDriverId()).isEqualTo(driverId);
        assertThat(delivered.getCustomerId()).isEqualTo(customerId);
        assertThat(delivered.getRestaurantId()).isEqualTo(restaurantId);

        // Internal review-eligibility requires ROLE_SERVICE
        var previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            "order-service",
                            "n/a",
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
            var eligibility = internalOrderController.reviewEligibility(orderId, customerId, restaurantId);
            assertThat(eligibility.eligible()).isTrue();
            assertThat(eligibility.reason()).containsIgnoringCase("eligible");
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private Order placeOrder() {
        return sagaOrchestrator.placeOrder(
                customerId,
                restaurantId,
                "1 Nguyen Hue, District 1, HCMC",
                "core-flow-" + UUID.randomUUID(),
                List.of(new OrderSagaOrchestrator.RequestedItem(menuItemId, 1)));
    }

    private void publishPickedUpInTransitAndCompleted(UUID orderId) {
        deliveryLifecycleEventListener.onEvent(json(driverAssigned(orderId, UUID.randomUUID(), 1)));
        deliveryLifecycleEventListener.onEvent(json(pickedUp(orderId, UUID.randomUUID(), 2)));
        deliveryLifecycleEventListener.onEvent(json(inTransit(orderId, UUID.randomUUID(), 3)));
        deliveryLifecycleEventListener.onEvent(json(completed(orderId, UUID.randomUUID(), 4)));
    }

    private String json(ObjectNode envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ObjectNode driverAssigned(UUID orderId, UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        ObjectNode driver = payload.putObject("driver");
        driver.put("driverId", driverId.toString());
        driver.put("fullName", "Nguyen Van A");
        driver.put("phone", "0900123456");
        driver.put("vehicleType", "MOTORBIKE");
        driver.put("licensePlate", "59A1-12345");
        payload.put("assignedAt", Instant.parse("2026-07-22T10:00:00Z").toString());
        return envelope(eventId, EventContracts.DRIVER_ASSIGNED, sequence, payload, deliveryId);
    }

    private ObjectNode pickedUp(UUID orderId, UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("driverId", driverId.toString());
        payload.put("pickedUpAt", Instant.parse("2026-07-22T10:05:00Z").toString());
        return envelope(eventId, EventContracts.DELIVERY_PICKED_UP, sequence, payload, deliveryId);
    }

    private ObjectNode inTransit(UUID orderId, UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("driverId", driverId.toString());
        payload.put("deliveryStartedAt", Instant.parse("2026-07-22T10:10:00Z").toString());
        return envelope(eventId, EventContracts.DELIVERY_IN_TRANSIT, sequence, payload, deliveryId);
    }

    private ObjectNode completed(UUID orderId, UUID eventId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", orderId.toString());
        payload.put("deliveryId", deliveryId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("driverId", driverId.toString());
        payload.put("deliveredAt", Instant.parse("2026-07-22T10:30:00Z").toString());
        return envelope(eventId, EventContracts.DELIVERY_COMPLETED, sequence, payload, deliveryId);
    }

    private ObjectNode envelope(UUID eventId, String eventType, long sequence, ObjectNode payload, UUID aggregateId) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", eventId.toString());
        root.put("eventType", eventType);
        root.put("eventVersion", 1);
        root.put("occurredAt", Instant.parse("2026-07-22T10:00:00Z").toString());
        root.put("aggregateType", "Delivery");
        root.put("aggregateId", aggregateId.toString());
        root.put("aggregateSequence", sequence);
        root.set("payload", payload);
        return root;
    }
}
