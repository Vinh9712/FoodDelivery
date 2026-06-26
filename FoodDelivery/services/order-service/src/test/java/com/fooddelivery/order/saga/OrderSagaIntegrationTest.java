package com.fooddelivery.order.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OutboxEvent;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm thử tích hợp đầu cuối cho Saga Orchestrator.
 * <p>
 * Sử dụng WireMock để giả lập Payment, Delivery, Notification Service.
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

    private static WireMockServer paymentServer;
    private static WireMockServer deliveryServer;
    private static WireMockServer notificationServer;

    @Autowired
    private OrderSagaOrchestrator sagaOrchestrator;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startWireMockServers() {
        paymentServer = new WireMockServer(wireMockConfig().port(PAYMENT_PORT));
        deliveryServer = new WireMockServer(wireMockConfig().port(DELIVERY_PORT));
        notificationServer = new WireMockServer(wireMockConfig().port(NOTIFICATION_PORT));

        paymentServer.start();
        deliveryServer.start();
        notificationServer.start();
    }

    @AfterAll
    static void stopWireMockServers() {
        paymentServer.stop();
        deliveryServer.stop();
        notificationServer.stop();
    }

    @BeforeEach
    void resetStubs() {
        paymentServer.resetAll();
        deliveryServer.resetAll();
        notificationServer.resetAll();

        // Notification luôn trả về thành công (fire-and-forget)
        notificationServer.stubFor(
                post(urlEqualTo("/api/notifications"))
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
                post(urlEqualTo("/api/payments"))
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
                post(urlEqualTo("/api/deliveries"))
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

        // ── Act: Thực thi Saga ──
        var items = List.of(
                new OrderSagaOrchestrator.OrderItemInput(
                        UUID.randomUUID(), "Phở Bò Tái", "Phở bò truyền thống",
                        new BigDecimal("75000"), 2),
                new OrderSagaOrchestrator.OrderItemInput(
                        UUID.randomUUID(), "Trà Đá", null,
                        new BigDecimal("5000"), 2)
        );

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), UUID.randomUUID(),
                "123 Lê Lợi, Quận 1, TP.HCM",
                new BigDecimal("15000"),  // phí giao
                BigDecimal.ZERO,          // giảm giá
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
        deliveryServer.verify(1, postRequestedFor(urlEqualTo("/api/deliveries")));

        // 6. Verify Notification Service đã được gọi
        notificationServer.verify(
                com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly(1),
                postRequestedFor(urlEqualTo("/api/notifications")));
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
                post(urlEqualTo("/api/payments"))
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

        // ── Act: Tạo đơn hàng với giá trị > 500k ──
        var items = List.of(
                new OrderSagaOrchestrator.OrderItemInput(
                        UUID.randomUUID(), "Bò Wagyu A5", "Bò Nhật cao cấp",
                        new BigDecimal("600000"), 1)
        );

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), UUID.randomUUID(),
                "456 Nguyễn Huệ, Quận 1, TP.HCM",
                new BigDecimal("20000"),
                BigDecimal.ZERO,
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
        deliveryServer.verify(0, postRequestedFor(urlEqualTo("/api/deliveries")));
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
                post(urlEqualTo("/api/payments"))
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
                post(urlEqualTo("/api/deliveries"))
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
                post(urlEqualTo("/api/payments/refund"))
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

        // ── Act: Đơn hàng với địa chỉ chứa "Invalid" ──
        var items = List.of(
                new OrderSagaOrchestrator.OrderItemInput(
                        UUID.randomUUID(), "Cơm Tấm", "Cơm tấm sườn bì chả",
                        new BigDecimal("45000"), 1)
        );

        Order result = sagaOrchestrator.placeOrder(
                UUID.randomUUID(), UUID.randomUUID(),
                "789 Invalid Street, District X",  // Chứa "Invalid"
                new BigDecimal("15000"),
                BigDecimal.ZERO,
                items
        );

        // ── Assert ──
        // 1. API hoàn tiền được gọi thành công
        paymentServer.verify(1, postRequestedFor(urlEqualTo("/api/payments/refund")));

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
                postRequestedFor(urlEqualTo("/api/notifications")));
    }
}
