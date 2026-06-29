package com.fooddelivery.customer.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.customer.infrastructure.persistence.OutboxEventRepository;
import com.fooddelivery.customer.infrastructure.persistence.model.OutboxEvent;
import com.fooddelivery.commonevents.EventContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OutboxRelayScheduler(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<UUID> eventIds = outboxEventRepository.findUnpublishedEventIds();
        if (eventIds.isEmpty()) {
            return;
        }

        log.debug("Found {} potential unpublished outbox events to relay.", eventIds.size());

        for (UUID eventId : eventIds) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    java.util.Optional<OutboxEvent> optEvent = outboxEventRepository.findByIdForUpdate(eventId);
                    if (optEvent.isPresent()) {
                        OutboxEvent event = optEvent.get();
                        if (event.getPublishedAt() == null) {
                            try {
                                String messageJson = constructMessageJson(event);

                                // Synchronously send to Kafka for reliability
                                kafkaTemplate.send(EventContracts.CUSTOMER_EVENTS_TOPIC, event.getAggregateId().toString(), messageJson).get();

                                event.markPublished();
                                outboxEventRepository.save(event);
                                log.info("Successfully published outbox event {} of type {} to Kafka.", event.getId(), event.getEventType());
                            } catch (Exception ex) {
                                log.error("Failed to relay outbox event {}.", event.getId(), ex);
                                throw new RuntimeException("Kafka send failed", ex);
                            }
                        }
                    }
                });
            } catch (Exception ex) {
                log.warn("Failed to process outbox event {} in its own transaction: {}", eventId, ex.getMessage());
            }
        }
    }

    private String constructMessageJson(OutboxEvent event) throws Exception {
        Object payloadObj = objectMapper.readTree(event.getPayload());

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", event.getId().toString());
        envelope.put("eventVersion", 1);
        envelope.put("eventType", event.getEventType());
        envelope.put("occurredAt", event.getCreatedAt().toString());
        envelope.put("payload", payloadObj);

        return objectMapper.writeValueAsString(envelope);
    }
}
