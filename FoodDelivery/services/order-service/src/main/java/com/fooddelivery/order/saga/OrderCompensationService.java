package com.fooddelivery.order.saga;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.domain.model.valueobject.RefundStatus;
import com.fooddelivery.order.infrastructure.client.PaymentServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.PaymentResponse;
import com.fooddelivery.order.infrastructure.client.dto.RefundRequest;
import com.fooddelivery.order.infrastructure.client.dto.RefundResponse;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import feign.FeignException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Starts durable cancellation compensation and confirms refunds.
 * Paid-order cancellation stays in {@code CANCELLATION_PENDING} until refund is confirmed.
 * Never sets {@link PaymentStatus#REFUNDED} until remote refund confirmation.
 */
@Service
public class OrderCompensationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCompensationService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentServiceClient paymentClient;
    private final Clock clock;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final int maxAttempts;
    private final MeterRegistry meterRegistry;

    public OrderCompensationService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            PaymentServiceClient paymentClient,
            Clock clock,
            ObjectProvider<MeterRegistry> meterRegistry,
            @Value("${order.refund-reconciliation.initial-backoff:30s}") Duration initialBackoff,
            @Value("${order.refund-reconciliation.max-backoff:30m}") Duration maxBackoff,
            @Value("${order.refund-reconciliation.max-attempts:8}") int maxAttempts) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.paymentClient = paymentClient;
        this.clock = clock;
        this.initialBackoff = initialBackoff != null ? initialBackoff : Duration.ofSeconds(30);
        this.maxBackoff = maxBackoff != null ? maxBackoff : Duration.ofMinutes(30);
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : 8;
        this.meterRegistry = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry);
    }

    @Transactional
    public void start(UUID orderId, CancellationCode code, String reason, OrderEventPayloads.Source source) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLATION_PENDING) {
            if (order.getRefundStatus() == RefundStatus.PENDING) {
                attemptRefund(order);
            }
            return;
        }

        Instant now = clock.instant();
        order.beginCompensation(reason, code, source, now);
        order.scheduleFirstRefundAttempt(now);
        persist(order);

        order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        attemptRefund(order);
    }

    @Transactional
    public void onRefundConfirmed(UUID orderId, UUID paymentId, UUID refundId, BigDecimal amount, Instant refundedAt) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.confirmRefund(paymentId, refundId, amount, refundedAt != null ? refundedAt : clock.instant());
        persist(order);
        meterRegistry.counter("order_refund_reconciliation_total", "outcome", "confirmed").increment();
    }

    @Transactional
    public void reconcileRefund(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.CANCELLATION_PENDING) {
            return;
        }
        if (order.getRefundStatus() == RefundStatus.SUCCEEDED
                || order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            if (order.getStatus() == OrderStatus.CANCELLATION_PENDING) {
                order.confirmRefund(null, null, order.getTotalAmount(), clock.instant());
                persist(order);
            }
            return;
        }
        if (order.getRefundStatus() == RefundStatus.MANUAL_REVIEW) {
            tryConfirmFromLookup(order);
            return;
        }
        if (order.getRefundStatus() != RefundStatus.PENDING) {
            return;
        }

        if (tryConfirmFromLookup(order)) {
            return;
        }
        attemptRefund(order);
    }

    private boolean tryConfirmFromLookup(Order order) {
        try {
            PaymentResponse payment = paymentClient.getPaymentByOrderId(order.getId());
            if (payment == null || !StringUtils.hasText(payment.status())) {
                return false;
            }
            if ("REFUNDED".equalsIgnoreCase(payment.status())) {
                if (payment.orderId() != null && !payment.orderId().equals(order.getId())) {
                    markManualReview(order, "Payment order mismatch on refund lookup");
                    return true;
                }
                order.confirmRefund(null, null, order.getTotalAmount(), clock.instant());
                persist(order);
                meterRegistry.counter("order_refund_reconciliation_total", "outcome", "confirmed").increment();
                return true;
            }
            return false;
        } catch (FeignException.NotFound notFound) {
            recordRefundFailure(order, "payment not found for refund");
            return true;
        } catch (RuntimeException ex) {
            recordRefundFailure(order, ex.getMessage());
            return true;
        }
    }

    private void attemptRefund(Order order) {
        if (order.getStatus() != OrderStatus.CANCELLATION_PENDING
                || order.getRefundStatus() != RefundStatus.PENDING) {
            return;
        }
        String key = "refund:" + order.getId();
        RefundRequest request = new RefundRequest(order.getId(), order.getTotalAmount());
        try {
            RefundResponse response = paymentClient.refundPayment(key, request);
            if (response == null || !StringUtils.hasText(response.status())) {
                markManualReview(order, "Invalid refund response");
                return;
            }
            if (!"REFUNDED".equalsIgnoreCase(response.status())
                    && !"COMPLETED".equalsIgnoreCase(response.status())
                    && !"SUCCESS".equalsIgnoreCase(response.status())) {
                recordRefundFailure(order, "Unexpected refund status: " + response.status());
                return;
            }
            if (response.orderId() != null && !response.orderId().equals(order.getId())) {
                markManualReview(order, "Refund response orderId mismatch");
                return;
            }
            BigDecimal amount = response.amount() != null ? response.amount() : order.getTotalAmount();
            Instant refundedAt = response.refundedAt() != null ? response.refundedAt() : clock.instant();
            order.confirmRefund(response.paymentId(), response.refundId(), amount, refundedAt);
            persist(order);
            meterRegistry.counter("order_refund_reconciliation_total", "outcome", "confirmed").increment();
        } catch (RuntimeException ex) {
            recordRefundFailure(order, ex.getMessage());
        }
    }

    private void recordRefundFailure(Order order, String error) {
        Instant now = clock.instant();
        int nextAttemptNumber = order.getRefundAttempts() + 1;
        if (nextAttemptNumber >= maxAttempts) {
            order.recordRefundAttemptFailure(error, null, now);
            order.markRefundManualReview(
                    "Refund retry exhausted after " + maxAttempts + " attempts: " + error, now);
            persist(order);
            meterRegistry.counter("order_refund_reconciliation_total", "outcome", "manual_review").increment();
            log.warn("Order {} refund moved to MANUAL_REVIEW after {} attempts", order.getId(), maxAttempts);
            return;
        }
        Duration backoff = computeBackoff(order.getRefundAttempts());
        order.recordRefundAttemptFailure(error, now.plus(backoff), now);
        persist(order);
        meterRegistry.counter("order_refund_reconciliation_total", "outcome", "backoff").increment();
    }

    private void markManualReview(Order order, String reason) {
        Instant now = clock.instant();
        if (order.getRefundStatus() == RefundStatus.PENDING) {
            order.markRefundManualReview(reason, now);
            persist(order);
            meterRegistry.counter("order_refund_reconciliation_total", "outcome", "manual_review").increment();
        }
    }

    private Duration computeBackoff(int previousAttempts) {
        int exp = Math.min(previousAttempts, 12);
        Duration candidate = initialBackoff.multipliedBy(1L << exp);
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }

    private void persist(Order order) {
        if (!order.getPendingOutboxEvents().isEmpty()) {
            outboxEventRepository.saveAll(order.getPendingOutboxEvents());
            order.clearPendingOutboxEvents();
        }
        orderRepository.save(order);
    }
}
