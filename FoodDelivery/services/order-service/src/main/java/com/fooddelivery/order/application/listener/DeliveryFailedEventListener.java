package com.fooddelivery.order.application.listener;

import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.PaymentStatus;
import com.fooddelivery.order.infrastructure.client.PaymentServiceClient;
import com.fooddelivery.order.infrastructure.client.dto.RefundRequest;
import com.fooddelivery.order.infrastructure.repository.OrderRepository;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import com.fooddelivery.order.infrastructure.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryFailedEventListener {

    private static final String CONSUMER_NAME = "order-service-delivery-failed";

    private final OrderRepository orderRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final OutboxEventRepository outboxEventRepository;

    @KafkaListener(topics = "delivery.failed", groupId = "order-service")
    @Transactional
    public void onDeliveryFailed(Map<String, Object> event) {
        UUID eventId = UUID.fromString(event.get("eventId").toString());
        if (processedEventRepository.existsByEventIdAndConsumer(eventId, CONSUMER_NAME)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        UUID orderId = UUID.fromString(payload.get("orderId").toString());
        String reason = String.valueOf(payload.getOrDefault("reason", "Delivery failed"));
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.CANCELLED) {
            paymentServiceClient.refundPayment(new RefundRequest(orderId, order.getTotalAmount()));
            order.updatePaymentStatus(PaymentStatus.REFUNDED);
            order.cancel("Delivery failed: " + reason);
            outboxEventRepository.saveAll(order.getPendingOutboxEvents());
            order.clearPendingOutboxEvents();
            orderRepository.save(order);
        }

        processedEventRepository.markProcessed(eventId, CONSUMER_NAME);
        log.info("Compensated delivery failure for order {} from event {}", orderId, eventId);
    }
}
