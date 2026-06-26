package com.fooddelivery.delivery.application.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
20. * Background scheduler that polls the {@code outbox_events} table for unpublished
21. * events and relays them reliably to Kafka.
22. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventPublisherScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000) // Run every 2 seconds
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEvent> unpublishedEvents = outboxEventRepository.findByPublishedFalseOrderByOccurredAtAsc();
        if (unpublishedEvents.isEmpty()) {
            return;
        }

        log.info("Found {} unpublished outbox events in delivery-service. Relaying to Kafka...", unpublishedEvents.size());
        for (OutboxEvent event : unpublishedEvents) {
            try {
                // Parse payload string back into a Map/Object so Kafka JsonSerializer publishes a clean JSON object
                Object jsonPayload = objectMapper.readValue(event.getPayload(), Object.class);

                // Build standard envelope matching Kafka events across services
                Map<String, Object> eventEnvelope = Map.of(
                        "eventId", event.getId().toString(),
                        "eventType", event.getEventType().toUpperCase().replace(".", "_"),
                        "timestamp", event.getOccurredAt().toString(),
                        "payload", jsonPayload
                );

                // Publish to Kafka topic using event.getEventType() (e.g. "driver.assigned")
                // key is aggregateId (e.g., deliveryId)
                kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), eventEnvelope).get();

                event.markPublished();
                outboxEventRepository.save(event);
                log.info("Successfully published outbox event {} (type={}) to Kafka topic", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage(), e);
            }
        }
    }
}
