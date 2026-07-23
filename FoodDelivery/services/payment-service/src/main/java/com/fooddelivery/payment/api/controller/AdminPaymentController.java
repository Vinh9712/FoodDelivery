package com.fooddelivery.payment.api.controller;

import com.fooddelivery.payment.api.dto.PaymentResponse;
import com.fooddelivery.payment.application.PaymentApplicationService;
import com.fooddelivery.payment.domain.model.Payment;
import com.fooddelivery.payment.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.payment.infrastructure.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;
    private final PaymentApplicationService paymentApplicationService;

    @GetMapping
    public ResponseEntity<Page<PaymentResponse>> list(
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Payment> page = status == null
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return ResponseEntity.ok(page.map(paymentApplicationService::toPublicResponse));
    }
}
