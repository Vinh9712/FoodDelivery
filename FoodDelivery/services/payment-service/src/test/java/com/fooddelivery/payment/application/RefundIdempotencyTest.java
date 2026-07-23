package com.fooddelivery.payment.application;

import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.payment.api.dto.PaymentRequest;
import com.fooddelivery.payment.api.dto.RefundRequest;
import com.fooddelivery.payment.api.dto.RefundResponse;
import com.fooddelivery.payment.domain.exception.IdempotencyKeyAlreadyUsedException;
import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.payment.infrastructure.repository.IdempotencyKeyRepository;
import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.payment.infrastructure.repository.PaymentRepository;
import com.fooddelivery.payment.infrastructure.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RefundIdempotencyTest {

    @Autowired
    private PaymentApplicationService service;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private UUID orderId;
    private UUID paymentId;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        outboxRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();

        orderId = UUID.randomUUID();
        amount = new BigDecimal("125000");
        var payment = service.processPayment("pay-" + orderId,
                new PaymentRequest(orderId, UUID.randomUUID(), amount));
        assertThat(payment.status()).isEqualTo("SUCCESS");
        paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();
    }

    @Test
    void sameKeyAndRequestReturnsTheSameRefund() {
        String key = "refund:" + orderId;
        RefundRequest request = new RefundRequest(orderId, amount);

        RefundResponse first = service.refund(key, request);
        RefundResponse second = service.refund(key, request);

        assertThat(second.refundId()).isEqualTo(first.refundId());
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(refundRepository.countByPayment_Id(paymentId)).isEqualTo(1);
        assertThat(outboxRepository.findByAggregateTypeAndAggregateId("Payment", paymentId))
                .filteredOn(e -> e.getEventType().equals(EventContracts.PAYMENT_REFUNDED))
                .hasSize(1);
    }

    @Test
    void sameKeyWithDifferentAmountReturnsConflict() {
        String key = "refund:" + orderId;
        service.refund(key, new RefundRequest(orderId, amount));

        assertThatThrownBy(() -> service.refund(key, new RefundRequest(orderId, new BigDecimal("1"))))
                .isInstanceOf(IdempotencyKeyAlreadyUsedException.class);
        assertThat(refundRepository.countByPayment_Id(paymentId)).isEqualTo(1);
    }

    @Test
    void differentKeyAgainstSamePaymentReturnsExistingRefund() {
        RefundResponse first = service.refund("refund:" + orderId, new RefundRequest(orderId, amount));
        RefundResponse second = service.refund("other-key:" + orderId, new RefundRequest(orderId, amount));

        assertThat(second.refundId()).isEqualTo(first.refundId());
        assertThat(refundRepository.countByPayment_Id(paymentId)).isEqualTo(1);
        assertThat(outboxRepository.findAll())
                .filteredOn(e -> e.getEventType().equals(EventContracts.PAYMENT_REFUNDED))
                .hasSize(1);
    }

    @Test
    void paymentRefundedPayloadUsesOrderIdPartitionKey() {
        service.refund("refund:" + orderId, new RefundRequest(orderId, amount));

        OutboxEvent event = outboxRepository.findByAggregateTypeAndAggregateId("Payment", paymentId).stream()
                .filter(e -> EventContracts.PAYMENT_REFUNDED.equals(e.getEventType()))
                .findFirst()
                .orElseThrow();
        assertThat(event.getPartitionKey()).isEqualTo(orderId.toString());
        assertThat(event.getPayload().path("paymentId").asText()).isEqualTo(paymentId.toString());
        assertThat(event.getPayload().path("refundId").asText()).isNotBlank();
        assertThat(event.getPayload().path("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(event.getPayload().path("amount").asText()).isEqualTo("125000");
        assertThat(event.getPayload().path("currency").asText()).isEqualTo("VND");
    }
}
