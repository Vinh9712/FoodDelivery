package com.fooddelivery.delivery.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DeliveryOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeliveryOutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DeliveryOutboxTopicMapper topicMapper;
    private final ObjectMapper objectMapper;
    private final Duration sendTimeout;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final int maxAttempts;

    public DeliveryOutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            DeliveryOutboxTopicMapper topicMapper,
            ObjectMapper objectMapper,
            @Value("${app.delivery.outbox.relay.send-timeout:5s}") Duration sendTimeout,
            @Value("${app.delivery.outbox.relay.retry-base-delay:5s}") Duration retryBaseDelay,
            @Value("${app.delivery.outbox.relay.retry-max-delay:5m}") Duration retryMaxDelay,
            @Value("${app.delivery.outbox.relay.max-attempts:10}") int maxAttempts) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topicMapper = topicMapper;
        this.objectMapper = objectMapper;
        this.sendTimeout = sendTimeout;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Publish a single due outbox event in its own transaction (REQUIRES_NEW).
     * Waits for broker ACK before marking published.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElse(null);
        Instant now = Instant.now();
        if (event == null || !event.canPublish(now)) {
            return;
        }

        try {
            Object payload = objectMapper.readValue(event.getPayload(), Object.class);
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", event.getId().toString());
            envelope.put("eventType", event.getEventType());
            envelope.put("timestamp", event.getOccurredAt().toString());
            envelope.put("payload", payload);

            String topic = topicMapper.topicFor(event.getEventType());
            kafkaTemplate.send(topic, event.getAggregateId().toString(), envelope)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            event.markPublished();
            log.info("Published delivery outbox event {} ({})", event.getId(), event.getEventType());
        } catch (Exception ex) {
            String error = rootMessage(ex);
            int nextAttempt = event.getAttempts() + 1;
            if (nextAttempt >= maxAttempts) {
                event.markDeadLettered(error);
                log.error("Delivery outbox event {} moved to dead letter after {} attempts: {}",
                        event.getId(), nextAttempt, error);
            } else {
                Instant retryAt = now.plus(backoffFor(nextAttempt));
                event.recordFailure(error, retryAt);
                log.warn("Delivery outbox event {} failed; retry {} scheduled at {}: {}",
                        event.getId(), nextAttempt, retryAt, error);
            }
        }
    }

    private Duration backoffFor(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        long delayMillis;
        try {
            delayMillis = Math.multiplyExact(retryBaseDelay.toMillis(), multiplier);
        } catch (ArithmeticException ex) {
            delayMillis = retryMaxDelay.toMillis();
        }
        return Duration.ofMillis(Math.min(delayMillis, retryMaxDelay.toMillis()));
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
