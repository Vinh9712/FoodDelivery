package com.fooddelivery.order.saga;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.client.DeliveryServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryResponse;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryStatusResponse;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Reconciles ambiguous delivery scheduling for READY_FOR_PICKUP orders.
 * Always looks up remote delivery state before posting schedule; never refunds on timeout/5xx.
 */
@Service
public class OrderDeliveryReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(OrderDeliveryReconciliationService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DeliveryServiceClient deliveryClient;
    private final OrderCompensationService compensationService;
    private final Clock clock;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final int attentionAttempts;
    private final MeterRegistry meterRegistry;

    public OrderDeliveryReconciliationService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            DeliveryServiceClient deliveryClient,
            OrderCompensationService compensationService,
            Clock clock,
            ObjectProvider<MeterRegistry> meterRegistry,
            @Value("${order.delivery-reconciliation.initial-backoff:30s}") Duration initialBackoff,
            @Value("${order.delivery-reconciliation.max-backoff:5m}") Duration maxBackoff,
            @Value("${order.delivery-reconciliation.attention-attempts:8}") int attentionAttempts) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.deliveryClient = deliveryClient;
        this.compensationService = compensationService;
        this.clock = clock;
        this.initialBackoff = initialBackoff != null ? initialBackoff : Duration.ofSeconds(30);
        this.maxBackoff = maxBackoff != null ? maxBackoff : Duration.ofMinutes(5);
        this.attentionAttempts = attentionAttempts > 0 ? attentionAttempts : 8;
        this.meterRegistry = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry);
    }

    @Transactional
    public void reconcile(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.READY_FOR_PICKUP) {
            return;
        }

        Instant now = clock.instant();
        DeliveryStatusResponse remote;
        try {
            remote = deliveryClient.findByOrderId(orderId);
        } catch (FeignException.NotFound notFound) {
            scheduleIdempotent(order, now);
            return;
        } catch (RuntimeException ex) {
            recordLookupFailure(order, ex, now);
            return;
        }

        if (remote == null || remote.deliveryId() == null) {
            scheduleIdempotent(order, now);
            return;
        }

        applyRemoteState(order, remote, now);
    }

    private void scheduleIdempotent(Order order, Instant now) {
        String key = "delivery-schedule:" + order.getId();
        DeliveryRequest request = new DeliveryRequest(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getPickupAddressSnapshot(),
                order.getDeliveryAddressSnapshot());
        try {
            DeliveryResponse scheduled = deliveryClient.schedule(key, request);
            if (scheduled == null || scheduled.deliveryId() == null) {
                recordLookupFailure(order, new IllegalStateException("schedule returned empty delivery"), now);
                return;
            }
            applyRemoteState(order, DeliveryStatusResponse.from(scheduled), now);
        } catch (RuntimeException ex) {
            recordLookupFailure(order, ex, now);
        }
    }

    private void applyRemoteState(Order order, DeliveryStatusResponse remote, Instant now) {
        String status = normalize(remote.status());
        if (remote.isTerminalFailure() || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            String reason = remote.failureReason() != null
                    ? remote.failureReason()
                    : (remote.message() != null ? remote.message() : "Delivery " + status);
            // Compensation commits the same aggregate in REQUIRES_NEW phases. Do not
            // leave this outer transaction holding a dirty, stale Order instance.
            compensationService.start(
                    order.getId(),
                    CancellationCode.DELIVERY_FAILED,
                    reason,
                    OrderEventPayloads.Source.DELIVERY_RECONCILIATION);
            return;
        }

        order.attachDelivery(remote.deliveryId());
        if (remote.driverId() != null) {
            try {
                order.assignDriver(remote.driverId());
            } catch (RuntimeException ignored) {
                // continue catch-up
            }
        }

        catchUpLifecycle(order, remote, status, now);
        persist(order);
    }

    private void catchUpLifecycle(Order order, DeliveryStatusResponse remote, String status, Instant now) {
        if (status == null
                || status.equals("PENDING")
                || status.equals("FINDING_DRIVER")
                || status.equals("DRIVER_ASSIGNED")
                || status.equals("ASSIGNED")) {
            return;
        }

        Instant pickedUpAt = firstNonNull(remote.pickedUpAt(), now);
        Instant startedAt = firstNonNull(remote.deliveryStartedAt(), pickedUpAt, now);
        Instant deliveredAt = firstNonNull(remote.deliveredAt(), startedAt, now);

        if (needsPickedUp(status) && order.getStatus() == OrderStatus.READY_FOR_PICKUP) {
            order.markPickedUp(pickedUpAt, OrderEventPayloads.Source.DELIVERY_RECONCILIATION);
        }
        if (needsDelivering(status) && order.getStatus() == OrderStatus.PICKED_UP) {
            order.markDelivering(startedAt, OrderEventPayloads.Source.DELIVERY_RECONCILIATION);
        }
        if ("DELIVERED".equals(status) && order.getStatus() == OrderStatus.DELIVERING) {
            order.markDelivered(deliveredAt, OrderEventPayloads.Source.DELIVERY_RECONCILIATION);
        }
    }

    private static boolean needsPickedUp(String status) {
        return "PICKED_UP".equals(status) || "DELIVERING".equals(status) || "DELIVERED".equals(status);
    }

    private static boolean needsDelivering(String status) {
        return "DELIVERING".equals(status) || "DELIVERED".equals(status);
    }

    private void recordLookupFailure(Order order, Exception ex, Instant now) {
        Instant next = now.plus(computeBackoff(order.getDeliveryScheduleAttempts()));
        order.recordDeliveryScheduleFailure(ex.getMessage(), next, now);
        if (order.getDeliveryScheduleAttempts() >= attentionAttempts) {
            meterRegistry.counter("order_delivery_reconciliation_attention_total").increment();
            log.warn("Delivery reconciliation attention: orderId={} attempts={}",
                    order.getId(), order.getDeliveryScheduleAttempts());
        }
        meterRegistry.counter("order_delivery_reconciliation_total", "outcome", "backoff").increment();
        persist(order);
    }

    private Duration computeBackoff(int previousAttempts) {
        int exp = Math.min(previousAttempts, 10);
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

    private static String normalize(String status) {
        return status == null ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    private static Instant firstNonNull(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return Instant.EPOCH;
    }
}
