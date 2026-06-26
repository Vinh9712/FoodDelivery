package com.fooddelivery.payment.application;

import com.fooddelivery.payment.domain.model.Payment;
import com.fooddelivery.payment.domain.model.PaymentStatus;
import com.fooddelivery.payment.domain.repository.PaymentRepository;
import com.fooddelivery.payment.infrastructure.messaging.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Core payment business logic.
 *
 * <p>In a real system, this would call a payment gateway (VNPay, Stripe, etc.).
 * For demo purposes, payments under 10,000,000 VND are auto-approved.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final BigDecimal APPROVAL_LIMIT = new BigDecimal("10000000");

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;

    /**
     * Initiate and process a payment for the given order.
     * Idempotent: if a payment already exists for this order, no new record is created.
     */
    @Transactional
    public Payment processPayment(UUID orderId, BigDecimal amount, String currency) {
        // Idempotency check
        return paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> doProcess(orderId, amount, currency));
    }

    private Payment doProcess(UUID orderId, BigDecimal amount, String currency) {
        // Simulate payment gateway decision
        boolean approved = amount.compareTo(APPROVAL_LIMIT) <= 0;

        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .currency(currency)
                .status(approved ? PaymentStatus.COMPLETED : PaymentStatus.FAILED)
                .failureReason(approved ? null : "Amount exceeds single-order limit")
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment {} for order {} → {}", payment.getId(), orderId, payment.getStatus());

        if (approved) {
            eventPublisher.publishProcessed(payment);
        } else {
            eventPublisher.publishFailed(payment);
        }
        return payment;
    }

    /**
     * Retrieve payment details by order ID.
     */
    @Transactional(readOnly = true)
    public Payment getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No payment found for order: " + orderId));
    }

    /**
     * Retrieve payment details by payment ID.
     */
    @Transactional(readOnly = true)
    public Payment getById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("No payment found for payment ID: " + paymentId));
    }
}
