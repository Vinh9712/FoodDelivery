package com.fooddelivery.order.application.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.order.infrastructure.persistence.ConsumedAggregateSequence;
import com.fooddelivery.order.infrastructure.persistence.DeferredIntegrationEvent;
import com.fooddelivery.order.infrastructure.repository.ConsumedAggregateSequenceRepository;
import com.fooddelivery.order.infrastructure.repository.DeferredIntegrationEventRepository;
import com.fooddelivery.order.infrastructure.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SequencedEventProcessorTest {

    private static final String CONSUMER = "order-delivery-v1";
    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final Map<String, ConsumedAggregateSequence> sequences = new HashMap<>();
    private final List<DeferredIntegrationEvent> deferred = new ArrayList<>();
    private final Map<String, Boolean> processed = new HashMap<>();

    private ConsumedAggregateSequenceRepository sequenceRepository;
    private DeferredIntegrationEventRepository deferredRepository;
    private ProcessedEventRepository processedEventRepository;
    private SequencedEventProcessor processor;
    private SequencedEventHandler handler;

    private UUID aggregateId;

    @BeforeEach
    void setUp() {
        sequences.clear();
        deferred.clear();
        processed.clear();

        sequenceRepository = mock(ConsumedAggregateSequenceRepository.class);
        deferredRepository = mock(DeferredIntegrationEventRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        handler = mock(SequencedEventHandler.class);

        when(sequenceRepository.findForUpdate(anyString(), anyString(), any()))
                .thenAnswer(inv -> Optional.ofNullable(
                        sequences.get(key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)))));
        when(sequenceRepository.save(any(ConsumedAggregateSequence.class))).thenAnswer(inv -> {
            ConsumedAggregateSequence c = inv.getArgument(0);
            sequences.put(key(c.getConsumerName(), c.getAggregateType(), c.getAggregateId()), c);
            return c;
        });
        when(sequenceRepository.findCurrent(anyString(), anyString(), any())).thenAnswer(inv ->
                Optional.ofNullable(sequences.get(key(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2))))
                        .map(ConsumedAggregateSequence::getLastAppliedSequence));

        when(processedEventRepository.existsByEventIdAndConsumer(any(), anyString()))
                .thenAnswer(inv -> processed.containsKey(inv.getArgument(0) + "|" + inv.getArgument(1)));
        doAnswer(inv -> {
            processed.put(inv.getArgument(0) + "|" + inv.getArgument(1), true);
            return null;
        }).when(processedEventRepository).markProcessed(any(), anyString());

        when(deferredRepository.findByConsumerNameAndEventId(anyString(), any()))
                .thenAnswer(inv -> deferred.stream()
                        .filter(e -> e.getConsumerName().equals(inv.getArgument(0))
                                && e.getEventId().equals(inv.getArgument(1)))
                        .findFirst());
        when(deferredRepository.findByConsumerNameAndAggregateTypeAndAggregateIdAndAggregateSequence(
                anyString(), anyString(), any(), anyLong()))
                .thenAnswer(inv -> deferred.stream()
                        .filter(e -> e.getConsumerName().equals(inv.getArgument(0))
                                && e.getAggregateType().equals(inv.getArgument(1))
                                && e.getAggregateId().equals(inv.getArgument(2))
                                && e.getAggregateSequence() == (long) inv.getArgument(3))
                        .findFirst());
        when(deferredRepository.findWaitingForUpdate(anyString(), anyString(), any(), anyLong(), any()))
                .thenAnswer(inv -> deferred.stream()
                        .filter(e -> e.getConsumerName().equals(inv.getArgument(0))
                                && e.getAggregateType().equals(inv.getArgument(1))
                                && e.getAggregateId().equals(inv.getArgument(2))
                                && e.getAggregateSequence() == (long) inv.getArgument(3)
                                && e.getStatus() == inv.getArgument(4))
                        .findFirst());
        when(deferredRepository.existsByConsumerNameAndAggregateTypeAndAggregateIdAndAggregateSequence(
                anyString(), anyString(), any(), anyLong()))
                .thenAnswer(inv -> deferred.stream().anyMatch(e ->
                        e.getConsumerName().equals(inv.getArgument(0))
                                && e.getAggregateType().equals(inv.getArgument(1))
                                && e.getAggregateId().equals(inv.getArgument(2))
                                && e.getAggregateSequence() == (long) inv.getArgument(3)
                                && e.isWaiting()));
        when(deferredRepository.existsByAggregateTypeAndAggregateIdAndSequence(
                anyString(), anyString(), any(), anyLong()))
                .thenAnswer(inv -> deferredRepository
                        .existsByConsumerNameAndAggregateTypeAndAggregateIdAndAggregateSequence(
                                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
        when(deferredRepository.save(any(DeferredIntegrationEvent.class))).thenAnswer(inv -> {
            DeferredIntegrationEvent e = inv.getArgument(0);
            deferred.removeIf(d -> d.getId().equals(e.getId()));
            deferred.add(e);
            return e;
        });
        when(deferredRepository.findById(any())).thenAnswer(inv ->
                deferred.stream().filter(e -> e.getId().equals(inv.getArgument(0))).findFirst());
        doAnswer(inv -> {
            DeferredIntegrationEvent e = inv.getArgument(0);
            deferred.removeIf(d -> d.getId().equals(e.getId()));
            return null;
        }).when(deferredRepository).delete(any(DeferredIntegrationEvent.class));

        processor = new SequencedEventProcessor(
                sequenceRepository,
                deferredRepository,
                processedEventRepository,
                objectMapper,
                clock);
        aggregateId = UUID.randomUUID();
    }

    @Test
    void defersGapThenDrainsWhenMissingSequenceArrives() throws Exception {
        processor.process(CONSUMER, event(aggregateId, 2), raw(event(aggregateId, 2)), handler);
        assertThat(deferredRepository.existsByAggregateTypeAndAggregateIdAndSequence(
                CONSUMER, "Delivery", aggregateId, 2L)).isTrue();
        verifyNoInteractions(handler);

        processor.process(CONSUMER, event(aggregateId, 1), raw(event(aggregateId, 1)), handler);

        InOrder order = inOrder(handler);
        order.verify(handler).apply(argThat(e -> e.aggregateSequence() == 1));
        order.verify(handler).apply(argThat(e -> e.aggregateSequence() == 2));
        assertThat(sequenceRepository.findCurrent(CONSUMER, "Delivery", aggregateId)).hasValue(2L);
        assertThat(deferred).isEmpty();
    }

    @Test
    void duplicateEventIdAndStaleSequenceAreNoOps() throws Exception {
        UUID eventId = UUID.randomUUID();
        processor.process(
                CONSUMER,
                eventWithId(eventId, aggregateId, 1),
                raw(eventWithId(eventId, aggregateId, 1)),
                handler);
        processor.process(
                CONSUMER,
                eventWithId(eventId, aggregateId, 1),
                raw(eventWithId(eventId, aggregateId, 1)),
                handler);
        processor.process(CONSUMER, event(aggregateId, 1), raw(event(aggregateId, 1)), handler);

        verify(handler, times(1)).apply(any());
        assertThat(sequenceRepository.findCurrent(CONSUMER, "Delivery", aggregateId)).hasValue(1L);
    }

    @Test
    void handlerFailureDoesNotMarkProcessedOrAdvanceSequence() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(handler).apply(any());

        assertThatThrownBy(() -> processor.process(
                CONSUMER, event(aggregateId, 1), raw(event(aggregateId, 1)), handler))
                .isInstanceOf(IllegalStateException.class);

        assertThat(processed).isEmpty();
        assertThat(sequences.values())
                .allMatch(c -> c.getLastAppliedSequence() == 0L);
    }

    @Test
    void malformedJsonThrowsBeforeInboxMutation() {
        assertThatThrownBy(() -> processor.parseAndValidate("{not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed");
        assertThat(sequences).isEmpty();
        assertThat(deferred).isEmpty();
        assertThat(processed).isEmpty();
    }

    @Test
    void nonPositiveSequenceThrowsBeforeInboxMutation() {
        ObjectNode node = baseEnvelope(UUID.randomUUID(), aggregateId, 0);
        assertThatThrownBy(() -> processor.parseAndValidate(write(node)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateSequence");
        assertThat(deferred).isEmpty();
        assertThat(processed).isEmpty();
    }

    @Test
    void unsupportedVersionThrowsBeforeInboxMutation() {
        ObjectNode node = baseEnvelope(UUID.randomUUID(), aggregateId, 1);
        node.put("eventVersion", 2);
        assertThatThrownBy(() -> processor.parseAndValidate(write(node)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion");
    }

    @Test
    void aggregateIdMismatchThrowsBeforeInboxMutation() {
        ObjectNode node = baseEnvelope(UUID.randomUUID(), aggregateId, 1);
        ((ObjectNode) node.get("payload")).put("deliveryId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> processor.parseAndValidate(write(node)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregate ID mismatch");
        assertThat(deferred).isEmpty();
        assertThat(processed).isEmpty();
    }

    private static String key(String c, String t, UUID id) {
        return c + "|" + t + "|" + id;
    }

    private IntegrationEventEnvelope<JsonNode> event(UUID aggregateId, long sequence) {
        return eventWithId(UUID.randomUUID(), aggregateId, sequence);
    }

    private IntegrationEventEnvelope<JsonNode> eventWithId(UUID eventId, UUID aggregateId, long sequence) {
        return processor.parseAndValidate(write(baseEnvelope(eventId, aggregateId, sequence)));
    }

    private ObjectNode baseEnvelope(UUID eventId, UUID aggregateId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("deliveryId", aggregateId.toString());
        payload.put("customerId", UUID.randomUUID().toString());
        payload.put("driverId", UUID.randomUUID().toString());
        payload.put("deliveredAt", NOW.toString());

        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", eventId.toString());
        root.put("eventType", EventContracts.DELIVERY_COMPLETED);
        root.put("eventVersion", 1);
        root.put("occurredAt", NOW.toString());
        root.put("aggregateType", "Delivery");
        root.put("aggregateId", aggregateId.toString());
        root.put("aggregateSequence", sequence);
        root.set("payload", payload);
        return root;
    }

    private String raw(IntegrationEventEnvelope<JsonNode> envelope) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", envelope.eventId().toString());
        root.put("eventType", envelope.eventType());
        root.put("eventVersion", envelope.eventVersion());
        root.put("occurredAt", envelope.occurredAt().toString());
        root.put("aggregateType", envelope.aggregateType());
        root.put("aggregateId", envelope.aggregateId().toString());
        root.put("aggregateSequence", envelope.aggregateSequence());
        root.set("payload", envelope.payload());
        return write(root);
    }

    private String write(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
