package com.fooddelivery.payment;

import com.fooddelivery.payment.api.dto.PaymentRequest;
import com.fooddelivery.payment.api.dto.RefundRequest;
import com.fooddelivery.payment.application.PaymentApplicationService;
import com.fooddelivery.payment.domain.model.Payment;
import com.fooddelivery.payment.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.payment.infrastructure.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PaymentServiceApplicationTests {

    @Autowired
    private PaymentApplicationService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Test
    void shouldCreateAndProcessPayment() {
        // Given
        PaymentRequest request = new PaymentRequest(
                orderId,
                customerId,
                new BigDecimal("100000"),
                "COD",
                "Test payment",
                null,  // returnUrl
                null   // cancelUrl
        );

        // When
        var response = paymentService.processPayment("test-key-001", request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isIn("SUCCESS", "PROCESSING");

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isIn(PaymentStatus.PAID, PaymentStatus.PROCESSING);
    }

    @Test
    void shouldRefundPayment() {
        // Given - create payment first
        PaymentRequest request = new PaymentRequest(
                orderId,
                customerId,
                new BigDecimal("100000"),
                "COD",
                "Test payment for refund",
                null,
                null
        );
        paymentService.processPayment("test-key-refund", request);

        // When
        RefundRequest refundRequest = new RefundRequest(
                orderId,
                new BigDecimal("100000"),
                "Customer requested refund"
        );
        var response = paymentService.refund(refundRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("REFUNDED");

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void shouldHandleIdempotency() {
        // Given
        PaymentRequest request = new PaymentRequest(
                orderId,
                customerId,
                new BigDecimal("100000"),
                "COD",
                "Test idempotency",
                null,
                null
        );

        // When - first call
        var response1 = paymentService.processPayment("idempotent-key-001", request);

        // Then - second call with same key
        var response2 = paymentService.processPayment("idempotent-key-001", request);

        // Then - both responses should be same
        assertThat(response1.orderId()).isEqualTo(response2.orderId());
        assertThat(response1.status()).isEqualTo(response2.status());
    }
}

