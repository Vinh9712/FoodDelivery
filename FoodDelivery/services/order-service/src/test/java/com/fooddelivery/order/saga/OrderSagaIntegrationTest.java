package com.fooddelivery.order.saga;

import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OutboxEvent;
import com.fooddelivery.order.domain.exception.InvalidOrderRequestException;
import com.fooddelivery.order.domain.exception.OrderDependencyException;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
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
 * Kiểm thử tích hợp đầu cuối cho Saga Orchestrator.
 * <p>
 * Sử dụng WireMock để giả lập Restaurant, Payment, Delivery, Notification Service.
 * H2 in-memory database cho Order DB (profile "test").
 * </p>
 *
 * <h3>Ca kiểm thử:</h3>
 * <ul>
 *   <li><b>Test 1 (Happy Path)</b>: Payment SUCCESS → Delivery ASSIGNED → Order PAID, driver assigned</li>
 *   <li><b>Test 2 (Payment Failed)</b>: amount > 500k → Order CANCELLED, no delivery call</li>
 *   <li><b>Test 3 (Delivery Failed)</b>: address "Invalid" → refund → Order CANCELLED</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderSagaIntegrationTest {

    // WireMock ports cho từng service
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

        // Notification luôn trả về thành công (fire-and-forget)
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

    // ══════════════════════════════════════════════════════════════════════
    // CA KIỂM THỬ 1: HAPPY PATH — Thành công toàn phần
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Happy Path: Payment SUCCESS → Delivery ASSIGNED → Order PAID, driver assigned")
    void test_happyPath_orderCompletedSuccessfully() {
        // ── Arrange: Giả lập Payment trả về SUCCESS ──
        var expectedDriverId = UUID.randomUUID();

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

        // Giả lập Delivery trả về ASSIGNED kèm driverId
        deliveryServer.stubFor(
                post(urlEqualTo("/internal/v1/deliveries"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "orderId": null,
                                          "status": "ASSIGNED",
                                          "driverId": "%s",
                                          "message": "Tài xế đã được phân bổ"
                                        }
                                        """.formatted(expectedDriverId))));

        var restaurantId = UUID.randomUUID();
        var phoId = UUID.randomUUID();
        var teaId = UUID.randomUUID();
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "restaurantId": "%s",
                                          "subtotal": 160000,
                                          "items": [
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
                                        }
                                        """.formatted(restaurantId, phoId, teaId))));

        // ── Act: Chỉ gửi ID và số lượng; mọi giá trị tiền đến từ server ──
        var items = List.of(
                new OrderSagaOrchestrator.RequestedItem(phoId, 2),
                new OrderSagaOrchestrator.RequestedItem(teaId, 2)
        );

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId,
                "123 Lê Lợi, Quận 1, TP.HCM",
                items
        );

        // ── Assert ──
        // 1. Trạng thái cuối cùng là CONFIRMED (sau khi thanh toán và gán tài xế thành công)
        Order savedOrder = orderRepository.findWithHistoryById(result.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // 2. driver_id được cập nhật
        assertThat(savedOrder.getDriverId()).isEqualTo(expectedDriverId);

        // 3. Lịch sử trạng thái đầy đủ: PENDING → CONFIRMED
        var history = savedOrder.getStatusHistory();
        assertThat(history).hasSizeGreaterThanOrEqualTo(2);
        assertThat(history.stream().anyMatch(h -> h.getToStatus() == OrderStatus.PENDING)).isTrue();
        assertThat(history.stream().anyMatch(h -> h.getToStatus() == OrderStatus.CONFIRMED)).isTrue();

        // 4. Outbox events được tạo
        List<OutboxEvent> events = outboxEventRepository
                .findByAggregateTypeAndAggregateId("Order", result.getId());
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> "OrderCreated".equals(e.getEventType()))).isTrue();

        // 5. Verify Delivery Service đã được gọi
        deliveryServer.verify(1, postRequestedFor(urlEqualTo("/internal/v1/deliveries")));

        // 6. Verify Notification Service đã được gọi
        notificationServer.verify(
                com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly(1),
                postRequestedFor(urlEqualTo("/internal/v1/notifications")));

        // 7. Payment nhận subtotal từ restaurant-service + phí giao hàng do order-service cấu hình.
        paymentServer.verify(1, postRequestedFor(urlEqualTo("/internal/v1/payments"))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("175000"))));
    }

    // ══════════════════════════════════════════════════════════════════════
    // CA KIỂM THỬ 2: THANH TOÁN THẤT BẠI
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Payment Failed: amount > 500k → Order CANCELLED, no delivery call")
    void test_paymentFailed_orderCancelled() {
        // ── Arrange: Giả lập Payment trả về FAILED ──
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
                                          "message": "Sự cố số dư tài khoản"
                                        }
                                        """)));

        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "restaurantId": "%s",
                                          "subtotal": 600000,
                                          "items": [{
                                            "menuItemId": "%s",
                                            "itemName": "Bò Wagyu A5",
                                            "description": "Bò Nhật cao cấp",
                                            "unitPrice": 600000,
                                            "quantity": 1,
                                            "lineTotal": 600000
                                          }]
                                        }
                                        """.formatted(restaurantId, itemId))));

        // ── Act: Restaurant service quyết định giá trị đơn hàng > 500k ──
        var items = List.of(
                new OrderSagaOrchestrator.RequestedItem(itemId, 1)
        );

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId,
                "456 Nguyễn Huệ, Quận 1, TP.HCM",
                items
        );

        // ── Assert ──
        // 1. Trạng thái đơn hàng là CANCELLED
        Order savedOrder = orderRepository.findWithHistoryById(result.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // 2. Lịch sử ghi nhận "Thanh toán thất bại"
        var history = savedOrder.getStatusHistory();
        assertThat(history.stream().anyMatch(h ->
                h.getToStatus() == OrderStatus.CANCELLED
                && h.getNote() != null
                && h.getNote().contains("Thanh toán thất bại")
        )).isTrue();

        // 3. KHÔNG có cuộc gọi nào gửi tới Delivery Service
        deliveryServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/deliveries")));
    }

    // ══════════════════════════════════════════════════════════════════════
    // CA KIỂM THỬ 3: GIAO VẬN THẤT BẠI — Trigger bù trừ tài chính
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Delivery Failed: address 'Invalid' → refund called → Order CANCELLED")
    void test_deliveryFailed_compensatingTransactionTriggered() {
        // ── Arrange: Payment thành công, Delivery thất bại ──
        paymentServer.stubFor(
                post(urlEqualTo("/internal/v1/payments"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "orderId": null,
                                          "status": "SUCCESS",
                                          "transactionId": "TXN-COMP-001",
                                          "message": "Thanh toán thành công"
                                        }
                                        """)));

        deliveryServer.stubFor(
                post(urlEqualTo("/internal/v1/deliveries"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "orderId": null,
                                          "status": "FAILED",
                                          "driverId": null,
                                          "message": "Vùng giao hàng không hỗ trợ"
                                        }
                                        """)));

        // Giả lập Refund trả về REFUNDED
        paymentServer.stubFor(
                post(urlEqualTo("/internal/v1/payments/refund"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "orderId": null,
                                          "status": "REFUNDED",
                                          "message": "Hoàn tiền thành công"
                                        }
                                        """)));

        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "restaurantId": "%s",
                                          "subtotal": 45000,
                                          "items": [{
                                            "menuItemId": "%s",
                                            "itemName": "Cơm Tấm",
                                            "description": "Cơm tấm sườn bì chả",
                                            "unitPrice": 45000,
                                            "quantity": 1,
                                            "lineTotal": 45000
                                          }]
                                        }
                                        """.formatted(restaurantId, itemId))));

        // ── Act: Đơn hàng với địa chỉ chứa "Invalid" ──
        var items = List.of(
                new OrderSagaOrchestrator.RequestedItem(itemId, 1)
        );

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId,
                "789 Invalid Street, District X",  // Chứa "Invalid"
                items
        );

        // ── Assert ──
        // 1. API hoàn tiền được gọi thành công
        paymentServer.verify(1, postRequestedFor(urlEqualTo("/internal/v1/payments/refund")));

        // 2. Trạng thái cuối cùng là CANCELLED
        Order savedOrder = orderRepository.findWithHistoryById(result.getId()).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // 3. Lịch sử ghi nhận "Lỗi phân bổ giao vận"
        var history = savedOrder.getStatusHistory();
        assertThat(history.stream().anyMatch(h ->
                h.getToStatus() == OrderStatus.CANCELLED
                && h.getNote() != null
                && h.getNote().contains("Lỗi phân bổ giao vận")
        )).isTrue();

        // 4. Notification đã được gửi (thông báo hủy + hoàn tiền)
        notificationServer.verify(
                com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly(1),
                postRequestedFor(urlEqualTo("/internal/v1/notifications")));
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
                                .withBody("""
                                        {
                                          "restaurantId": "%s",
                                          "subtotal": 1,
                                          "items": [{
                                            "menuItemId": "%s",
                                            "itemName": "Pho",
                                            "description": null,
                                            "unitPrice": 75000,
                                            "quantity": 2,
                                            "lineTotal": 150000
                                          }]
                                        }
                                        """.formatted(restaurantId, itemId))));

        assertThatThrownBy(() -> sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId, "123 Test Street",
                List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 2))))
                .isInstanceOf(OrderDependencyException.class);

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
        paymentServer.verify(0, postRequestedFor(urlEqualTo("/internal/v1/payments")));
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
        var driverId = UUID.randomUUID();
        var clientRequestId = "checkout-" + UUID.randomUUID();
        stubSingleItemQuote(restaurantId, itemId, "50000");
        paymentServer.stubFor(post(urlEqualTo("/internal/v1/payments"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"orderId":null,"status":"SUCCESS","transactionId":"TXN-IDEMPOTENT","message":"ok"}
                                """)));
        deliveryServer.stubFor(post(urlEqualTo("/internal/v1/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"orderId":null,"status":"ASSIGNED","driverId":"%s","message":"ok"}
                                """.formatted(driverId))));

        var requestedItems = List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 1));
        Order first = sagaOrchestrator.placeOrder(
                customerId, restaurantId, "Idempotent Street", clientRequestId, requestedItems);
        Order replay = sagaOrchestrator.placeOrder(
                customerId, restaurantId, "Tampered retry address", clientRequestId, requestedItems);

        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(orderRepository.findByCustomerIdAndClientRequestId(customerId, clientRequestId)
                .orElseThrow().getId()).isEqualTo(first.getId());
        restaurantServer.verify(1, postRequestedFor(urlEqualTo(
                "/internal/v1/restaurants/" + restaurantId + "/menu/quote")));
        paymentServer.verify(1, postRequestedFor(urlEqualTo("/internal/v1/payments"))
                .withHeader("Idempotency-Key", equalTo("order-payment:" + first.getId())));
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Lost payment response is reconciled instead of cancelling a paid order")
    void test_lostPaymentResponse_reconcilesPaymentStatus() {
        var restaurantId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var driverId = UUID.randomUUID();
        stubSingleItemQuote(restaurantId, itemId, "50000");
        paymentServer.stubFor(post(urlEqualTo("/internal/v1/payments"))
                .willReturn(aResponse().withStatus(503)));
        paymentServer.stubFor(get(urlMatching("/internal/v1/payments/orders/.*"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"orderId":null,"status":"SUCCESS","transactionId":"TXN-RECOVERED","message":"ok"}
                                """)));
        deliveryServer.stubFor(post(urlEqualTo("/internal/v1/deliveries"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"orderId":null,"status":"ASSIGNED","driverId":"%s","message":"ok"}
                                """.formatted(driverId))));

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), restaurantId, "Recovery Street", "recover-" + UUID.randomUUID(),
                List.of(new OrderSagaOrchestrator.RequestedItem(itemId, 1)));

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        paymentServer.verify(1, postRequestedFor(urlEqualTo("/internal/v1/payments")));
        paymentServer.verify(1, getRequestedFor(urlMatching("/internal/v1/payments/orders/.*")));
    }

    private void stubSingleItemQuote(UUID restaurantId, UUID itemId, String price) {
        restaurantServer.stubFor(
                post(urlEqualTo("/internal/v1/restaurants/" + restaurantId + "/menu/quote"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                          "restaurantId": "%s",
                                          "subtotal": %s,
                                          "items": [{
                                            "menuItemId": "%s",
                                            "itemName": "Server Item",
                                            "description": null,
                                            "unitPrice": %s,
                                            "quantity": 1,
                                            "lineTotal": %s
                                          }]
                                        }
                                        """.formatted(restaurantId, price, itemId, price, price))));
    }
}
