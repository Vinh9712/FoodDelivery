package com.fooddelivery.order.saga;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OutboxEvent;
import com.fooddelivery.order.domain.exception.InvalidOrderRequestException;
import com.fooddelivery.order.domain.exception.OrderDependencyException;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end saga tests for payment-only placement (Task 5).
 * Delivery is not scheduled from payment success.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderSagaIntegrationTest {

    private static final int PAYMENT_PORT = 18082;
    private static final int DELIVERY_PORT = 18083;
    private static final int NOTIFICATION_PORT = 18084;
    private static final int RESTAURANT_PORT = 18085;

    private static WireMockServer paymentServer;
    private static WireMockServer deliveryServer;
    private static WireMockServer notificationServer;
    private static WireMockServer restaurantServer;

    @Autowired
    private OrderSagaOrchestrator sagaOrchestrator;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeAll
    static void startWireMockServers() {
        paymentServer = new WireMockServer(wireMockConfig().port(PAYMENT_PORT));
        deliveryServer = new WireMockServer(wireMockConfig().port(DELIVERY_PORT));
        notificationServer = new WireMockServer(wireMockConfig().port(NOTIFICATION_PORT));
        restaurantServer = new WireMockServer(wireMockConfig().port(RESTAURANT_PORT));

        paymentServer.start();
        deliveryServer.start();
        notificationServer.start();
        restaurantServer.start();
    }

    @AfterAll
    static void stopWireMockServers() {
        paymentServer.stop();
        deliveryServer.stop();
        notificationServer.stop();
        restaurantServer.stop();
    }

    @BeforeEach
    void resetStubs() {
        paymentServer.resetAll();
        deliveryServer.resetAll();
        notificationServer.resetAll();
        restaurantServer.resetAll();

        notificationServer.stubFor(
                post(urlEqualTo("/internal/v1/notifications"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "notificationId": "%s",
                                          "status": "SENT",
                                          "sentAt": "2026-06-25T14:00:00Z",
                                          "message": "Thông báo đã gửi thành công"
                                        }
                                        """.formatted(UUID.randomUUID()))));
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Payment success stops at PAID, persists pickup snapshot, never schedules delivery")
    void paymentSuccessStopsAtPaidAndPersistsPickupSnapshot() {
        paymentServer.stubFor(
                post(urlEqualTo("/internal/v1/payments"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "orderId": null,
                                          "status": "SUCCESS",
                                          "transactionId": "TXN-HAPPY-001",
                                          "message": "Thanh toán thành công"
                                        }
                                        """)));

        var restaurantId = UUID.randomUUID();
        var phoId = UUID.randomUUID();
        var teaId = UUID.randomUUID();
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(quoteBodyWithPickup(restaurantId, """
                                        [
                                          {
                                            "menuItemId": "%s",
                                            "itemName": "Phở Bò Tái",
                                            "description": "Phở bò truyền thống",
                                            "unitPrice": 75000,
                                            "quantity": 2,
                                            "lineTotal": 150000
                                          },
                                          {
                                            "menuItemId": "%s",
                                            "itemName": "Trà Đá",
                                            "description": null,
                                            "unitPrice": 5000,
                                            "quantity": 2,
                                            "lineTotal": 10000
                                          }
                                        ]
                                        """.formatted(phoId, teaId), "160000"))));

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId,
                "123 Lê Lợi, Quận 1, TP.HCM",
                "request-happy-" + UUID.randomUUID(),
                List.of(
                        new OrderSagaOrchestrator.RequestedItem(phoId, 2),
                        new OrderSagaOrchestrator.RequestedItem(teaId, 2)
                )
        );

        Order savedOrder = orderRepository.findWithHistoryById(result.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(savedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedOrder.getRestaurantResponseDeadline()).isNotNull();
        assertThat(savedOrder.getPickupAddressSnapshot()).isNotNull();
        assertThat(savedOrder.getPickupAddressSnapshot().restaurantId()).isEqualTo(restaurantId);
        assertThat(savedOrder.getPickupAddressSnapshot().name()).isEqualTo("Demo Restaurant");
        assertThat(savedOrder.getDriverId()).isNull();

        var history = savedOrder.getStatusHistory();
        assertThat(history.stream().anyMatch(h -> h.getToStatus() == OrderStatus.PENDING)).isTrue();
        assertThat(history.stream().anyMatch(h -> h.getToStatus() == OrderStatus.PAID)).isTrue();

        List<OutboxEvent> events = outboxEventRepository
                .findByAggregateTypeAndAggregateId("Order", result.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> "OrderCreated".equals(e.getEventType()))).isTrue();

        deliveryServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/deliveries")));
        paymentServer.verify(1, postRequestedFor(urlEqualTo("/internal/v1/payments"))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("175000"))));
        paymentServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/payments/refund")));
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Explicit payment failure cancels without delivery or refund")
    void explicitPaymentFailureCancelsWithoutDeliveryOrRefund() {
        paymentServer.stubFor(
                post(urlEqualTo("/internal/v1/payments"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "orderId": null,
                                          "status": "FAILED",
                                          "transactionId": null,
                                          "message": "declined"
                                        }
                                        """)));

        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        stubSingleItemQuote(restaurantId, itemId, "600000");

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId,
                "456 Nguyễn Huệ, Quận 1, TP.HCM",
                "request-fail-" + UUID.randomUUID(),
                List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 1))
        );

        Order savedOrder = orderRepository.findWithHistoryById(result.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(savedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(savedOrder.getRefundStatus()).isEqualTo(RefundStatus.NOT_REQUIRED);
        assertThat(savedOrder.getPickupAddressSnapshot()).isNotNull();

        deliveryServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/deliveries")));
        paymentServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/payments/refund")));
        notificationServer.verify(
                moreThanOrExactly(1),
                postRequestedFor(urlEqualTo("/internal/v1/notifications")));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Payment reconciliation also stops at PAID without scheduling delivery")
    void paymentReconciliationAlsoStopsAtPaidWithoutScheduling() {
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        stubSingleItemQuote(restaurantId, itemId, "50000");
        paymentServer.stubFor(post(urlEqualTo("/internal/v1/payments"))
                .willReturn(aResponse().withStatus(503)));
        paymentServer.stubFor(get(urlMatching("/internal/v1/payments/orders/.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"orderId":null,"status":"SUCCESS","transactionId":"TXN-RECOVERED","message":"ok"}
                                """)));

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId, "Recovery Street", "recover-" + UUID.randomUUID(),
                List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 1)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.getPickupAddressSnapshot()).isNotNull();
        assertThat(result.getPickupAddressSnapshot().restaurantId()).isEqualTo(restaurantId);
        deliveryServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/deliveries")));
        paymentServer.verify(1, postRequestedFor(urlEqualTo("/internal/v1/payments")));
        paymentServer.verify(1, getRequestedFor(urlMatching("/internal/v1/payments/orders/.*")));
        paymentServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/payments/refund")));
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Inconsistent restaurant quote is rejected before order persistence and payment")
    void test_inconsistentQuote_rejectedBeforeCreatingOrder() {
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        long ordersBefore = orderRepository.count();
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(quoteBodyWithPickup(restaurantId, """
                                        [{
                                          "menuItemId": "%s",
                                          "itemName": "Pho",
                                          "description": null,
                                          "unitPrice": 75000,
                                          "quantity": 2,
                                          "lineTotal": 150000
                                        }]
                                        """.formatted(itemId), "1"))));

        assertThatThrownBy(() -> sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId, "123 Test Street",
                List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 2))))
                .isInstanceOf(OrderDependencyException.class);

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        paymentServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/payments")));
        deliveryServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/deliveries")));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Restaurant business rejection becomes an invalid customer order")
    void test_restaurantRejectsItems_mapsToInvalidOrder() {
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        long ordersBefore = orderRepository.count();
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(400)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"message\":\"Menu item is unavailable\"}")));

        assertThatThrownBy(() -> sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId, "123 Test Street",
                List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 1))))
                .isInstanceOf(InvalidOrderRequestException.class);

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        paymentServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/payments")));
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Client idempotency key returns the original order without charging twice")
    void test_duplicateClientRequest_returnsOriginalOrder() {
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var clientRequestId = "checkout-" + UUID.randomUUID();
        stubSingleItemQuote(restaurantId, itemId, "50000");
        paymentServer.stubFor(post(urlEqualTo("/internal/v1/payments"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"orderId":null,"status":"SUCCESS","transactionId":"TXN-IDEMPOTENT","message":"ok"}
                                """)));

        var requestedItems = List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 1));
        Order first = sagaOrchestrator.placeOrder(
                customerId, restaurantId, "Idempotent Street", clientRequestId, requestedItems);
        Order replay = sagaOrchestrator.placeOrder(
                customerId, restaurantId, "Tampered retry address", clientRequestId, requestedItems);

        assertThat(first.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(orderRepository.findByCustomerIdAndClientRequestId(customerId, clientRequestId)
                .orElseThrow().getId()).isEqualTo(first.getId());
        restaurantServer.verify(1, postRequestedFor(urlEqualTo(
                "/internal/v1/restaurants/" + restaurantId + "/menu/quote")));
        paymentServer.verify(1, postRequestedFor(urlEqualTo("/internal/v1/payments"))
                .withHeader("Idempotency-Key", equalTo("order-payment:" + first.getId())));
        deliveryServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/deliveries")));
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Quote without pickup snapshot is rejected before order creation")
    void quoteWithoutPickupIsRejected() {
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        long ordersBefore = orderRepository.count();
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "restaurantId": "%s",
                                          "subtotal": 50000,
                                          "items": [{
                                            "menuItemId": "%s",
                                            "itemName": "Server Item",
                                            "description": null,
                                            "unitPrice": 50000,
                                            "quantity": 1,
                                            "lineTotal": 50000
                                          }]
                                        }
                                        """.formatted(restaurantId, itemId))));

        assertThatThrownBy(() -> sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId, "No Pickup Street",
                List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 1))))
                .isInstanceOf(OrderDependencyException.class);

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        paymentServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/payments")));
    }

    private void stubSingleItemQuote(UUID restaurantId, UUID itemId, String price) {
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(quoteBodyWithPickup(restaurantId, """
                                        [{
                                          "menuItemId": "%s",
                                          "itemName": "Server Item",
                                          "description": null,
                                          "unitPrice": %s,
                                          "quantity": 1,
                                          "lineTotal": %s
                                        }]
                                        """.formatted(itemId, price, price), price))));
    }

    private static String quoteBodyWithPickup(UUID restaurantId, String itemsJson, String subtotal) {
        return """
                {
                  "restaurantId": "%s",
                  "subtotal": %s,
                  "pickup": {
                    "restaurantId": "%s",
                    "name": "Demo Restaurant",
                    "phone": "0901000000",
                    "addressText": "12 Le Loi, District 1",
                    "latitude": null,
                    "longitude": null
                  },
                  "items": %s
                }
                """.formatted(restaurantId, subtotal, restaurantId, itemsJson);
    }
}
