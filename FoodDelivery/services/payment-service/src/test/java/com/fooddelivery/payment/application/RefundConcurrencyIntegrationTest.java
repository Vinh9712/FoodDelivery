package com.fooddelivery.payment.application;

import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.payment.api.dto.PaymentRequest;
import com.fooddelivery.payment.api.dto.RefundRequest;
import com.fooddelivery.payment.api.dto.RefundResponse;
import com.fooddelivery.payment.infrastructure.repository.IdempotencyKeyRepository;
import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.payment.infrastructure.repository.PaymentRepository;
import com.fooddelivery.payment.infrastructure.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two concurrent refund calls with distinct keys against the same paid payment
 * must yield a single refund row / PaymentRefunded outbox event.
 */
@SpringBootTest
class RefundConcurrencyIntegrationTest {

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
        amount = new BigDecimal("99000");
        service.processPayment("pay-" + orderId, new PaymentRequest(orderId, UUID.randomUUID(), amount));
        paymentId = paymentRepository.findByOrderId(orderId).orElseThrow().getId();
    }

    @Test
    void concurrentDistinctKeysProduceSingleRefund() throws Exception {
        RefundRequest request = new RefundRequest(orderId, amount);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<RefundResponse>> tasks = List.of(
                    () -> service.refund("refund-a:" + orderId, request),
                    () -> service.refund("refund-b:" + orderId, request));
            List<Future<RefundResponse>> futures = pool.invokeAll(tasks);
            List<RefundResponse> responses = new ArrayList<>();
            for (Future<RefundResponse> future : futures) {
                responses.add(future.get());
            }

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).refundId()).isEqualTo(responses.get(1).refundId());
            assertThat(refundRepository.countByPayment_Id(paymentId)).isEqualTo(1);
            assertThat(outboxRepository.findByAggregateTypeAndAggregateId("Payment", paymentId))
                    .filteredOn(e -> e.getEventType().equals(EventContracts.PAYMENT_REFUNDED))
                    .hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
