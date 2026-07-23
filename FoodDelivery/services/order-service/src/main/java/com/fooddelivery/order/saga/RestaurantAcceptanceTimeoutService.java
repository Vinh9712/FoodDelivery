package com.fooddelivery.order.saga;

import com.fooddelivery.commonevents.order.OrderEventPayloads;
import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.CancellationCode;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * Claims overdue PAID orders for restaurant acceptance timeout exactly once.
 * Races with restaurant accept via optimistic {@code @Version}; loser is not retried as override.
 * Uses {@link TransactionTemplate} so optimistic-lock failures on commit are catchable.
 */
@Service
public class RestaurantAcceptanceTimeoutService {

    public static final String METRIC = "restaurant_acceptance_timeout_total";
    public static final String REASON = "Restaurant did not accept before deadline";

    private static final Logger log = LoggerFactory.getLogger(RestaurantAcceptanceTimeoutService.class);

    private final OrderRepository orderRepository;
    private final OrderCompensationService compensationService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate requiresNew;

    public RestaurantAcceptanceTimeoutService(
            OrderRepository orderRepository,
            OrderCompensationService compensationService,
            Clock clock,
            PlatformTransactionManager transactionManager,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this.orderRepository = orderRepository;
        this.compensationService = compensationService;
        this.clock = clock;
        this.meterRegistry = meterRegistry.getIfAvailable(() -> Metrics.globalRegistry);
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public enum Outcome {
        TIMED_OUT,
        ACCEPT_WON,
        NOT_DUE,
        MISSING
    }

    /**
     * Reload order in a fresh transaction, claim timeout if still eligible, start compensation once.
     */
    public Outcome processCandidate(UUID orderId) {
        try {
            return requiresNew.execute(status -> doProcess(orderId));
        } catch (OptimisticLockingFailureException ex) {
            meterRegistry.counter(METRIC, "outcome", "accept_won").increment();
            log.info("Restaurant accept won race against timeout for order {}", orderId);
            return Outcome.ACCEPT_WON;
        } catch (InvalidOrderStateException ex) {
            meterRegistry.counter(METRIC, "outcome", "accept_won").increment();
            log.info("Timeout abandoned for order {} — state no longer PAID: {}", orderId, ex.getMessage());
            return Outcome.ACCEPT_WON;
        }
    }

    private Outcome doProcess(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return Outcome.MISSING;
        }

        var now = clock.instant();
        if (!order.requestCancellationIfRestaurantTimedOut(now)) {
            if (order.getStatus() != OrderStatus.PAID) {
                meterRegistry.counter(METRIC, "outcome", "accept_won").increment();
                return Outcome.ACCEPT_WON;
            }
            return Outcome.NOT_DUE;
        }

        compensationService.start(
                orderId,
                CancellationCode.RESTAURANT_ACCEPTANCE_TIMEOUT,
                REASON,
                OrderEventPayloads.Source.SYSTEM_TIMEOUT);
        meterRegistry.counter(METRIC, "outcome", "timed_out").increment();
        log.info("Restaurant acceptance timeout claimed for order {}", orderId);
        return Outcome.TIMED_OUT;
    }
}
