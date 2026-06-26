package com.fooddelivery.payment.api.controller;

import com.fooddelivery.payment.application.PaymentService;
import com.fooddelivery.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * REST API for Payment resources.
 *
 * <p>Payments are normally auto-created when order.placed is received from Kafka.
 * The POST endpoint exists for manual triggering / testing.</p>
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Manually trigger payment processing for an order.
     * Useful for testing without Kafka.
     *
     * @param body JSON: { "orderId": "...", "amount": 150000, "currency": "VND" }
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@RequestBody CreatePaymentRequest body) {
        Payment payment = paymentService.processPayment(
                body.orderId(), body.amount(), body.currency());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    /**
     * Get payment status by order ID.
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getByOrderId(@PathVariable UUID orderId) {
        Payment payment = paymentService.getByOrderId(orderId);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    /**
     * Get payment by its own ID.
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getById(@PathVariable UUID paymentId) {
        try {
            Payment payment = paymentService.getById(paymentId);
            return ResponseEntity.ok(PaymentResponse.from(payment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Inner DTOs ───────────────────────────────────────────────────────

    public record CreatePaymentRequest(
            UUID orderId,
            BigDecimal amount,
            String currency
    ) {}

    public record PaymentResponse(
            String paymentId,
            String orderId,
            String status,
            String amount,
            String currency,
            String failureReason,
            String createdAt
    ) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(
                    p.getId().toString(),
                    p.getOrderId().toString(),
                    p.getStatus().name(),
                    p.getAmount().toPlainString(),
                    p.getCurrency(),
                    p.getFailureReason(),
                    p.getCreatedAt() != null ? p.getCreatedAt().toString() : null
            );
        }
    }
}
