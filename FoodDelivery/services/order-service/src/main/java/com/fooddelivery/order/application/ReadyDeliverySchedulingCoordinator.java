package com.fooddelivery.order.application;

import com.fooddelivery.order.infrastructure.client.DeliveryServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.DeliveryRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After-commit scheduling of delivery for {@link RestaurantOrderService.OrderReadyForPickup}.
 * Network failures leave the order untouched; reconciliation retries later.
 */
@Component
@RequiredArgsConstructor
public class ReadyDeliverySchedulingCoordinator {

    private final DeliveryServiceClient client;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReady(RestaurantOrderService.OrderReadyForPickup event) {
        try {
            client.schedule("delivery-schedule:" + event.orderId(), DeliveryRequest.from(event));
        } catch (RuntimeException exception) {
            LoggerFactory.getLogger(getClass()).warn(
                    "Delivery scheduling is ambiguous; reconciliation will retry order {}",
                    event.orderId());
        }
    }
}
