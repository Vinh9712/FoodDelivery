package com.fooddelivery.order.application.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.order.domain.model.OutboxEvent;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class OrderOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final OrderOutboxTopicMapper topicMapper;
    private final ObjectMapper objectMapper;
    private final Duration sendTimeout;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final int maxAttempts;

    public OrderOutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            OrderOutboxTopicMapper topicMapper,
            ObjectMapper objectMapper,
            @Value("${app.order.outbox.relay.send-timeout:5s}") Duration sendTimeout,
            @Value("${app.order.outbox.relay.retry-base-delay:5s}") Duration retryBaseDelay,
            @Value("${app.order.outbox.relay.retry-max-delay:5m}") Duration retryMaxDelay,
            @Value("${app.order.outbox.relay.max-attempts:10}") int maxAttempts) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topicMapper = topicMapper;
        this.objectMapper = objectMapper;
        this.sendTimeout = sendTimeout;
        this.retryBaseDelay = retryBaseDelay;
        this.retryMaxDelay = retryMaxDelay;
        this.maxAttempts = maxAttempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findByIdForUpdate(eventId).orElse(null);
        Instant now = Instant.now();
        if (event == null || !event.canPublish(now)) {
            return;
        }

        try {
            JsonNode payload = objectMapper.valueToTree(event.getPayload());
            IntegrationEventEnvelope<JsonNode> envelope = new IntegrationEventEnvelope<>(
                    event.getId(),
                    event.getEventType(),
                    event.getEventVersion(),
                    event.getCreatedAt(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getAggregateSequence(),
                    payload);

            String topic = topicMapper.topicFor(event.getEventType());
            String key = event.getPartitionKey() != null
                    ? event.getPartitionKey()
                    : event.getAggregateId().toString();
            kafkaTemplate.send(topic, key, envelope)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            event.markPublished();
            log.info("Published order outbox event {} ({})", event.getId(), event.getEventType());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            scheduleRetryOrDeadLetter(event, now, "Interrupted while waiting for Kafka ACK");
            log.warn("Order outbox event {} interrupted; interrupt flag restored", event.getId());
        } catch (ExecutionException | TimeoutException | RuntimeException ex) {
            scheduleRetryOrDeadLetter(event, now, rootMessage(ex));
        }
    }

    private void scheduleRetryOrDeadLetter(OutboxEvent event, Instant now, String error) {
        int nextAttempt = event.getAttempts() + 1;
        if (nextAttempt >= maxAttempts) {
            event.markDeadLettered(error);
            log.error("Order outbox event {} moved to dead letter after {} attempts: {}",
                    event.getId(), nextAttempt, error);
        } else {
            Instant retryAt = now.plus(backoffFor(nextAttempt));
            event.recordFailure(error, retryAt);
            log.warn("Order outbox event {} failed; retry {} scheduled at {}: {}",
                    event.getId(), nextAttempt, retryAt, error);
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
