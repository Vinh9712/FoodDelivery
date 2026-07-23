package com.fooddelivery.payment.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.payment.PaymentEventPayloads.PaymentFailed;
import com.fooddelivery.commonevents.payment.PaymentEventPayloads.PaymentRefunded;
import com.fooddelivery.commonevents.payment.PaymentEventPayloads.PaymentSucceeded;
import com.fooddelivery.payment.api.dto.PaymentRequest;
import com.fooddelivery.payment.api.dto.PaymentResponse;
import com.fooddelivery.payment.api.dto.RefundRequest;
import com.fooddelivery.payment.api.dto.RefundResponse;
import com.fooddelivery.payment.domain.exception.IdempotencyKeyAlreadyUsedException;
import com.fooddelivery.payment.domain.exception.InvalidPaymentRequestException;
import com.fooddelivery.payment.domain.exception.InvalidPaymentStateException;
import com.fooddelivery.payment.domain.exception.PaymentNotFoundException;
import com.fooddelivery.payment.domain.model.IdempotencyKey;
import com.fooddelivery.payment.domain.model.Payment;
import com.fooddelivery.payment.domain.model.Refund;
import com.fooddelivery.payment.domain.model.valueobject.CachedResponse;
import com.fooddelivery.payment.domain.model.valueobject.GatewayResponse;
import com.fooddelivery.payment.domain.model.valueobject.Money;
import com.fooddelivery.payment.domain.model.valueobject.PaymentMethod;
import com.fooddelivery.payment.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.payment.domain.model.valueobject.RefundStatus;
import com.fooddelivery.payment.domain.model.valueobject.RequestHash;
import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.payment.infrastructure.repository.IdempotencyKeyRepository;
import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.payment.infrastructure.repository.PaymentRepository;
import com.fooddelivery.payment.infrastructure.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentApplicationService {

    private static final String CURRENCY = "VND";

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
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

    /**
     * Idempotent refund under payment lock.
     * Same key+hash → replay; same key different hash → 409; existing refund other key → return that refund.
     */
    @Transactional
    public RefundResponse refund(String idempotencyKey, RefundRequest request) {
        validateIdempotencyKey(idempotencyKey);
        if (request == null || request.orderId() == null || request.amount() == null) {
            throw new InvalidPaymentRequestException("orderId and amount are required for refund");
        }
        String requestHash = hashRefund(request.orderId(), request.amount());

        Optional<Refund> byKey = refundRepository.findByIdempotencyKey(idempotencyKey);
        if (byKey.isPresent()) {
            Refund existing = byKey.get();
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new IdempotencyKeyAlreadyUsedException(idempotencyKey);
            }
            return toRefundResponse(existing);
        }

        Payment payment = paymentRepository.findByOrderIdForUpdate(request.orderId())
                .orElseThrow(() -> new PaymentNotFoundException(request.orderId()));

        // Re-check key under lock (race with concurrent first write)
        byKey = refundRepository.findByIdempotencyKey(idempotencyKey);
        if (byKey.isPresent()) {
            Refund existing = byKey.get();
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new IdempotencyKeyAlreadyUsedException(idempotencyKey);
            }
            return toRefundResponse(existing);
        }

        Optional<Refund> byPayment = refundRepository.findByPayment_Id(payment.getId());
        if (byPayment.isPresent()) {
            // One refund per payment — return existing regardless of key
            return toRefundResponse(byPayment.get());
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            // Legacy / already refunded without row visibility — still require amount match
            if (request.amount().compareTo(payment.getAmount().amount()) != 0) {
                throw new InvalidPaymentRequestException("Refund amount must equal the captured payment amount");
            }
            throw new InvalidPaymentRequestException("Payment is REFUNDED but refund row is missing");
        }

        if (request.amount().compareTo(payment.getAmount().amount()) != 0) {
            throw new InvalidPaymentRequestException("Refund amount must equal the captured payment amount");
        }

        try {
            Refund refund = payment.requestRefund(
                    new Money(request.amount()), "Order saga compensation", idempotencyKey, requestHash);
            refund.process("refund-" + UUID.randomUUID());
            refund.complete();
            paymentRepository.saveAndFlush(payment);
            publishRefunded(payment, refund);
            return toRefundResponse(refund);
        } catch (DataIntegrityViolationException | InvalidPaymentStateException race) {
            // Concurrent winner already inserted the unique payment_id refund
            return refundRepository.findByPayment_Id(payment.getId())
                    .or(() -> refundRepository.findByIdempotencyKey(idempotencyKey))
                    .map(this::toRefundResponse)
                    .orElseThrow(() -> race);
        }
    }

    /** Backward-compatible entry used by older callers — key derived from order. */
    @Transactional
    public RefundResponse refund(RefundRequest request) {
        if (request == null || request.orderId() == null) {
            throw new InvalidPaymentRequestException("orderId is required for refund");
        }
        return refund("refund:" + request.orderId(), request);
    }

    private Payment createAndProcessPayment(PaymentRequest request) {
        Payment payment = Payment.create(
                request.orderId(), request.customerId(), new Money(request.amount()), PaymentMethod.COD);
        payment.process();
        if (request.amount().compareTo(simulatorMaxAmount) > 0) {
            payment.markFailed("Payment amount exceeds the configured simulator limit");
            payment = paymentRepository.save(payment);
            publishFailed(payment);
        } else {
            var gatewayPayload = objectMapper.createObjectNode()
                    .put("provider", "local-simulator")
                    .put("approved", true);
            payment.markPaid("txn-" + UUID.randomUUID(), new GatewayResponse(gatewayPayload));
            payment = paymentRepository.save(payment);
            publishSucceeded(payment);
        }
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

    private RefundResponse toRefundResponse(Refund refund) {
        Payment payment = refund.getPayment();
        Instant refundedAt = refund.getRefundedAt();
        String status = refund.getStatus() == RefundStatus.COMPLETED ? "REFUNDED" : refund.getStatus().name();
        return new RefundResponse(
                payment.getOrderId(),
                status,
                "Payment refunded successfully",
                payment.getId(),
                refund.getId(),
                refund.getAmount().amount(),
                refundedAt);
    }

    private void publishSucceeded(Payment payment) {
        Instant paidAt = payment.getPaidAt() != null ? payment.getPaidAt() : Instant.now();
        enqueue(payment, EventContracts.PAYMENT_SUCCEEDED, new PaymentSucceeded(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                amountString(payment),
                CURRENCY,
                paidAt));
    }

    private void publishFailed(Payment payment) {
        String reason = payment.getFailedReason() != null
                ? payment.getFailedReason()
                : "Payment failed";
        enqueue(payment, EventContracts.PAYMENT_FAILED, new PaymentFailed(
                payment.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                amountString(payment),
                CURRENCY,
                reason,
                Instant.now()));
    }

    private void publishRefunded(Payment payment, Refund refund) {
        Instant refundedAt = refund.getRefundedAt() != null ? refund.getRefundedAt() : Instant.now();
        enqueue(payment, EventContracts.PAYMENT_REFUNDED, new PaymentRefunded(
                payment.getId(),
                refund.getId(),
                payment.getOrderId(),
                payment.getCustomerId(),
                amountString(payment),
                CURRENCY,
                refundedAt));
    }

    private void enqueue(Payment payment, String eventType, Object typedPayload) {
        long sequence = payment.nextEventSequence();
        paymentRepository.save(payment);
        JsonNode payload = objectMapper.valueToTree(typedPayload);
        outboxEventRepository.save(new OutboxEvent(
                "Payment",
                payment.getId(),
                eventType,
                1,
                sequence,
                payment.getOrderId().toString(),
                payload));
    }

    private String amountString(Payment payment) {
        return payment.getAmount().amount().stripTrailingZeros().toPlainString();
    }

    private RequestHash hash(PaymentRequest request) {
        String canonical = request.orderId() + "|" + request.customerId() + "|"
                + request.amount().stripTrailingZeros().toPlainString();
        return new RequestHash(sha256Hex(canonical));
    }

    private String hashRefund(UUID orderId, BigDecimal amount) {
        String canonical = orderId + "|" + amount.stripTrailingZeros().toPlainString();
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
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
