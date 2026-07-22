package com.fooddelivery.delivery.application.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.delivery.infrastructure.persistence.ConsumedAggregateSequence;
import com.fooddelivery.delivery.infrastructure.persistence.DeferredIntegrationEvent;
import com.fooddelivery.delivery.infrastructure.persistence.DeferredIntegrationEvent.Status;
import com.fooddelivery.delivery.infrastructure.repository.ConsumedAggregateSequenceRepository;
import com.fooddelivery.delivery.infrastructure.repository.DeferredIntegrationEventRepository;
import com.fooddelivery.delivery.infrastructure.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Sequenced inbox for order-family events in delivery-service: dedupe, stale, gap defer, drain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SequencedOrderEventProcessor {

    private final ConsumedAggregateSequenceRepository sequenceRepository;
    private final DeferredIntegrationEventRepository deferredRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IntegrationEventEnvelope<JsonNode> parseAndValidate(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("event payload is required");
        }
        final JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Malformed integration event JSON", ex);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Malformed integration event JSON");
        }

        UUID eventId = requireUuid(root, "eventId");
        String eventType = requireText(root, "eventType");
        int eventVersion = requirePositiveInt(root, "eventVersion");
        if (eventVersion != 1) {
            throw new IllegalArgumentException("unsupported eventVersion: " + eventVersion);
        }
        Instant occurredAt = requireInstant(root, "occurredAt");
        String aggregateType = requireText(root, "aggregateType");
        UUID aggregateId = requireUuid(root, "aggregateId");
        long aggregateSequence = requirePositiveLong(root, "aggregateSequence");
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("payload is required");
        }

        assertOrderAggregateConsistency(aggregateType, aggregateId, payload);

        return new IntegrationEventEnvelope<>(
                eventId,
                eventType,
                eventVersion,
                occurredAt,
                aggregateType,
                aggregateId,
                aggregateSequence,
                payload);
    }

    @Transactional
    public ProcessDecision process(
            String consumerName,
            IntegrationEventEnvelope<JsonNode> envelope,
            String rawJson,
            SequencedEventHandler handler) {
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName is required");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("envelope is required");
        }
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalArgumentException("rawJson is required");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler is required");
        }

        if (envelope.eventVersion() != 1) {
            throw new IllegalArgumentException("unsupported eventVersion: " + envelope.eventVersion());
        }
        if (envelope.aggregateSequence() < 1) {
            throw new IllegalArgumentException("aggregateSequence must be positive");
        }
        assertOrderAggregateConsistency(envelope.aggregateType(), envelope.aggregateId(), envelope.payload());

        Instant now = clock.instant();
        ConsumedAggregateSequence cursor = lockOrCreateCursor(
                consumerName, envelope.aggregateType(), envelope.aggregateId(), now);

        if (processedEventRepository.existsByEventIdAndConsumer(envelope.eventId(), consumerName)) {
            log.debug("Duplicate eventId {} for consumer {}", envelope.eventId(), consumerName);
            return ProcessDecision.DUPLICATE;
        }

        long last = cursor.getLastAppliedSequence();
        long sequence = envelope.aggregateSequence();

        if (sequence <= last) {
            processedEventRepository.markProcessed(envelope.eventId(), consumerName);
            return ProcessDecision.STALE;
        }

        if (sequence > last + 1) {
            upsertDeferred(consumerName, envelope, rawJson, now);
            return ProcessDecision.DEFERRED;
        }

        applyAndAdvance(consumerName, cursor, envelope, handler, now);
        drainContiguous(consumerName, cursor, handler, now);
        return ProcessDecision.APPLIED;
    }

    private void applyAndAdvance(
            String consumerName,
            ConsumedAggregateSequence cursor,
            IntegrationEventEnvelope<JsonNode> envelope,
            SequencedEventHandler handler,
            Instant now) {
        try {
            handler.apply(envelope);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Sequenced event handler failed", ex);
        }
        processedEventRepository.markProcessed(envelope.eventId(), consumerName);
        cursor.advanceTo(envelope.aggregateSequence(), now);
        sequenceRepository.save(cursor);
        deferredRepository
                .findByConsumerNameAndEventId(consumerName, envelope.eventId())
                .ifPresent(deferredRepository::delete);
    }

    private void drainContiguous(
            String consumerName,
            ConsumedAggregateSequence cursor,
            SequencedEventHandler handler,
            Instant now) {
        while (true) {
            long next = cursor.getLastAppliedSequence() + 1;
            var deferred = deferredRepository.findWaitingForUpdate(
                    consumerName,
                    cursor.getAggregateType(),
                    cursor.getAggregateId(),
                    next,
                    Status.WAITING_FOR_PREDECESSOR);
            if (deferred.isEmpty()) {
                return;
            }
            DeferredIntegrationEvent waiting = deferred.get();
            IntegrationEventEnvelope<JsonNode> nextEnvelope = parseAndValidate(waiting.getEventJson());
            if (processedEventRepository.existsByEventIdAndConsumer(nextEnvelope.eventId(), consumerName)) {
                deferredRepository.delete(waiting);
                cursor.advanceTo(next, now);
                sequenceRepository.save(cursor);
                continue;
            }
            applyAndAdvance(consumerName, cursor, nextEnvelope, handler, now);
            deferredRepository.findById(waiting.getId()).ifPresent(deferredRepository::delete);
        }
    }

    private void upsertDeferred(
            String consumerName,
            IntegrationEventEnvelope<JsonNode> envelope,
            String rawJson,
            Instant now) {
        if (deferredRepository.findByConsumerNameAndEventId(consumerName, envelope.eventId()).isPresent()) {
            return;
        }
        var bySequence = deferredRepository
                .findByConsumerNameAndAggregateTypeAndAggregateIdAndAggregateSequence(
                        consumerName,
                        envelope.aggregateType(),
                        envelope.aggregateId(),
                        envelope.aggregateSequence());
        if (bySequence.isPresent()) {
            DeferredIntegrationEvent existing = bySequence.get();
            if (!existing.getEventId().equals(envelope.eventId())) {
                throw new IllegalStateException(
                        "Conflicting deferred event for sequence " + envelope.aggregateSequence());
            }
            return;
        }
        deferredRepository.save(DeferredIntegrationEvent.waiting(
                consumerName,
                envelope.eventId(),
                envelope.aggregateType(),
                envelope.aggregateId(),
                envelope.aggregateSequence(),
                rawJson,
                now));
    }

    private ConsumedAggregateSequence lockOrCreateCursor(
            String consumerName, String aggregateType, UUID aggregateId, Instant now) {
        return sequenceRepository.findForUpdate(consumerName, aggregateType, aggregateId)
                .orElseGet(() -> sequenceRepository.save(
                        new ConsumedAggregateSequence(consumerName, aggregateType, aggregateId, 0L, now)));
    }

    private static void assertOrderAggregateConsistency(
            String aggregateType, UUID aggregateId, JsonNode payload) {
        if (!"Order".equals(aggregateType)) {
            return;
        }
        if (payload == null || !payload.isObject() || !payload.hasNonNull("orderId")) {
            return;
        }
        UUID orderId;
        try {
            orderId = UUID.fromString(payload.get("orderId").asText());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("payload.orderId is invalid", ex);
        }
        if (!orderId.equals(aggregateId)) {
            throw new IllegalArgumentException("aggregate ID mismatch");
        }
    }

    private static UUID requireUuid(JsonNode root, String field) {
        String text = requireText(root, field);
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " is invalid", ex);
        }
    }

    private static String requireText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return node.asText();
    }

    private static int requirePositiveInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new IllegalArgumentException(field + " is required");
        }
        int value = node.asInt();
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static long requirePositiveLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || !node.isNumber()) {
            throw new IllegalArgumentException(field + " is required");
        }
        long value = node.asLong();
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static Instant requireInstant(JsonNode root, String field) {
        String text = requireText(root, field);
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            throw new IllegalArgumentException(field + " is invalid", ex);
        }
    }
}
