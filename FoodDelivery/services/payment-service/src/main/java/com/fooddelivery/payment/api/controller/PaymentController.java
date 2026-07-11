package com.fooddelivery.payment.api.controller;

import com.fooddelivery.payment.api.dto.PaymentRequest;
import com.fooddelivery.payment.api.dto.PaymentResponse;
import com.fooddelivery.payment.api.dto.RefundRequest;
import com.fooddelivery.payment.api.dto.RefundResponse;
import com.fooddelivery.payment.application.PaymentApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SERVICE')")
public class PaymentController {

    private final PaymentApplicationService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.processPayment(idempotencyKey, request));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getByOrderId(orderId));
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundResponse> refundPayment(@Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refund(request));
    }
}
