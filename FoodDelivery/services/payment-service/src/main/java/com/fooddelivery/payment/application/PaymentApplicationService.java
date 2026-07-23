package com.fooddelivery.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.payment.api.dto.PaymentRequest;
import com.fooddelivery.payment.api.dto.PaymentResponse;
import com.fooddelivery.payment.api.dto.RefundRequest;
import com.fooddelivery.payment.api.dto.RefundResponse;
import com.fooddelivery.payment.domain.exception.IdempotencyKeyAlreadyUsedException;
import com.fooddelivery.payment.domain.exception.InvalidPaymentRequestException;
import com.fooddelivery.payment.domain.exception.PaymentNotFoundException;
import com.fooddelivery.payment.domain.model.IdempotencyKey;
import com.fooddelivery.payment.domain.model.Payment;
import com.fooddelivery.payment.domain.model.Refund;
import com.fooddelivery.payment.domain.model.valueobject.CachedResponse;
import com.fooddelivery.payment.domain.model.valueobject.GatewayResponse;
import com.fooddelivery.payment.domain.model.valueobject.Money;
import com.fooddelivery.payment.domain.model.valueobject.PaymentMethod;
import com.fooddelivery.payment.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.payment.domain.model.valueobject.RequestHash;
import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.payment.infrastructure.repository.IdempotencyKeyRepository;
import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.payment.infrastructure.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.payment.simulator.max-amount:500000}")
    private BigDecimal simulatorMaxAmount;

    @Transactional
    public PaymentResponse processPayment(String idempotencyKey, PaymentRequest request) {
        validateIdempotencyKey(idempotencyKey);
        RequestHash requestHash = hash(request);

        var existingKey = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        if (existingKey.isPresent()) {
            IdempotencyKey key = existingKey.get();
            if (!key.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyAlreadyUsedException(idempotencyKey);
            }
            if (key.getPaymentId() == null) {
                throw new InvalidPaymentRequestException("Idempotent payment is not attached to a payment");
            }
            return paymentRepository.findById(key.getPaymentId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new PaymentNotFoundException(request.orderId()));
        }

        Payment payment = paymentRepository.findByOrderId(request.orderId())
                .map(existing -> validateExistingPayment(existing, request))
                .orElseGet(() -> createAndProcessPayment(request));

        PaymentResponse response = toResponse(payment);
        IdempotencyKey key = IdempotencyKey.create(
                idempotencyKey, requestHash, Instant.now().plus(24, ChronoUnit.HOURS));
        key.attachPayment(payment.getId());
        key.cacheResponse(new CachedResponse(objectMapper.valueToTree(response)));
        idempotencyKeyRepository.save(key);
        return response;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    @Transactional
    public RefundResponse refund(RefundRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new PaymentNotFoundException(request.orderId()));
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return new RefundResponse(request.orderId(), "REFUNDED", "Payment was already refunded");
        }
        if (request.amount().compareTo(payment.getAmount().amount()) != 0) {
            throw new InvalidPaymentRequestException("Refund amount must equal the captured payment amount");
        }

        Refund refund = payment.requestRefund(new Money(request.amount()), "Order saga compensation");
        refund.process("refund-" + UUID.randomUUID());
        refund.complete();
        paymentRepository.save(payment);
        publish(payment, "PaymentRefunded", request.amount());
        return new RefundResponse(request.orderId(), "REFUNDED", "Payment refunded successfully");
    }

    private Payment createAndProcessPayment(PaymentRequest request) {
        Payment payment = Payment.create(
                request.orderId(), request.customerId(), new Money(request.amount()), PaymentMethod.COD);
        payment.process();
        if (request.amount().compareTo(simulatorMaxAmount) > 0) {
            payment.markFailed("Payment amount exceeds the configured simulator limit");
        } else {
            var gatewayPayload = objectMapper.createObjectNode()
                    .put("provider", "local-simulator")
                    .put("approved", true);
            payment.markPaid("txn-" + UUID.randomUUID(), new GatewayResponse(gatewayPayload));
        }
        payment = paymentRepository.save(payment);
        publish(payment, payment.getStatus() == PaymentStatus.PAID ? "PaymentSucceeded" : "PaymentFailed",
                request.amount());
        return payment;
    }

    private Payment validateExistingPayment(Payment payment, PaymentRequest request) {
        if (!payment.getCustomerId().equals(request.customerId())
                || payment.getAmount().amount().compareTo(request.amount()) != 0) {
            throw new InvalidPaymentRequestException("Order already has a payment with different immutable values");
        }
        return payment;
    }

    private PaymentResponse toResponse(Payment payment) {
        return switch (payment.getStatus()) {
            case PAID -> new PaymentResponse(payment.getOrderId(), "SUCCESS",
                    payment.getGatewayTransactionId(), "Payment completed");
            case FAILED -> new PaymentResponse(payment.getOrderId(), "FAILED", null,
                    payment.getFailedReason());
            case REFUNDED -> new PaymentResponse(payment.getOrderId(), "REFUNDED",
                    payment.getGatewayTransactionId(), "Payment refunded");
            case PENDING, PROCESSING -> new PaymentResponse(payment.getOrderId(), "PROCESSING", null,
                    "Payment is still processing");
            case CANCELLED -> new PaymentResponse(payment.getOrderId(), "FAILED", null,
                    "Payment was cancelled");
        };
    }

    private void publish(Payment payment, String eventType, BigDecimal amount) {
        var payload = objectMapper.createObjectNode()
                .put("paymentId", payment.getId().toString())
                .put("orderId", payment.getOrderId().toString())
                .put("customerId", payment.getCustomerId().toString())
                .put("status", payment.getStatus().name())
                .put("amount", amount.toPlainString());
        outboxEventRepository.save(new OutboxEvent("Payment", payment.getId(), eventType, payload));
    }

    private RequestHash hash(PaymentRequest request) {
        String canonical = request.orderId() + "|" + request.customerId() + "|"
                + request.amount().stripTrailingZeros().toPlainString();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new RequestHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey) || idempotencyKey.length() > 200) {
            throw new InvalidPaymentRequestException("A valid Idempotency-Key header is required");
        }
    }
}
