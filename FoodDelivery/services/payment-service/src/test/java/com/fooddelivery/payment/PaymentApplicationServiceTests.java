package com.fooddelivery.payment;

import com.fooddelivery.payment.api.dto.PaymentRequest;
import com.fooddelivery.payment.api.dto.RefundRequest;
import com.fooddelivery.payment.application.PaymentApplicationService;
import com.fooddelivery.payment.domain.exception.IdempotencyKeyAlreadyUsedException;
import com.fooddelivery.payment.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.payment.infrastructure.repository.IdempotencyKeyRepository;
import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.payment.infrastructure.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PaymentApplicationServiceTests {

    @Autowired
    private PaymentApplicationService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        idempotencyKeyRepository.deleteAll();
        outboxEventRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void duplicateIdempotencyKeyReturnsSameDurablePayment() {
        var request = request("125000");

        var first = paymentService.processPayment("pay-key-1", request);
        var replay = paymentService.processPayment("pay-key-1", request);

        assertThat(first.status()).isEqualTo("SUCCESS");
        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void sameKeyCannotBeReusedForDifferentImmutableValues() {
        var request = request("125000");
        paymentService.processPayment("pay-key-2", request);

        var tampered = new PaymentRequest(request.orderId(), request.customerId(), new BigDecimal("1"));

        assertThatThrownBy(() -> paymentService.processPayment("pay-key-2", tampered))
                .isInstanceOf(IdempotencyKeyAlreadyUsedException.class);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void differentKeyForSameOrderDoesNotChargeAgain() {
        var request = request("125000");

        var first = paymentService.processPayment("pay-key-3", request);
        var replay = paymentService.processPayment("pay-key-4", request);

        assertThat(replay.transactionId()).isEqualTo(first.transactionId());
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(2);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void refundIsDurableAndIdempotent() {
        var request = request("125000");
        paymentService.processPayment("pay-key-5", request);

        var refundRequest = new RefundRequest(request.orderId(), request.amount());
        var first = paymentService.refund(refundRequest);
        var replay = paymentService.refund(refundRequest);

        assertThat(first.status()).isEqualTo("REFUNDED");
        assertThat(replay.status()).isEqualTo("REFUNDED");
        assertThat(paymentRepository.findByOrderId(request.orderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(outboxEventRepository.count()).isEqualTo(2);
    }

    private PaymentRequest request(String amount) {
        return new PaymentRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(amount));
    }
}
