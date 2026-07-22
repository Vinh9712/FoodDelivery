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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeferredEventDrainSchedulerTest {

    private static final String CONSUMER = "order-delivery-v1";
    private static final Instant T0 = Instant.parse("2026-07-22T12:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, ConsumedAggregateSequence> sequences = new HashMap<>();
    private final List<DeferredIntegrationEvent> deferred = new ArrayList<>();
    private final Map<String, Boolean> processed = new HashMap<>();
    private final AtomicLong handlerCalls = new AtomicLong();

    private ConsumedAggregateSequenceRepository sequenceRepository;
    private DeferredIntegrationEventRepository deferredRepository;
    private ProcessedEventRepository processedEventRepository;
    private SequencedEventProcessor processor;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private SimpleMeterRegistry meterRegistry;
    private MutableClock clock;
    private DeferredEventDrainScheduler scheduler;
    private UUID aggregateId;

    @BeforeEach
    void setUp() {
        sequences.clear();
        deferred.clear();
        processed.clear();
        handlerCalls.set(0);
        aggregateId = UUID.randomUUID();

        sequenceRepository = mock(ConsumedAggregateSequenceRepository.class);
        deferredRepository = mock(DeferredIntegrationEventRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        kafkaTemplate = mock();
        meterRegistry = new SimpleMeterRegistry();
        clock = new MutableClock(T0);

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
        when(deferredRepository.findDueWaiting(any(), any())).thenAnswer(inv -> {
            Instant now = inv.getArgument(0);
            return deferred.stream()
                    .filter(DeferredIntegrationEvent::isWaiting)
                    .filter(e -> !e.getNextAttemptAt().isAfter(now))
                    .toList();
        });

        when(kafkaTemplate.send(any(Message.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        processor = new SequencedEventProcessor(
                sequenceRepository,
                deferredRepository,
                processedEventRepository,
                objectMapper,
                clock);

        SequencedConsumer consumer = new SequencedConsumer() {
            @Override
            public String consumerName() {
                return CONSUMER;
            }

            @Override
            public void handle(IntegrationEventEnvelope<JsonNode> envelope) {
                handlerCalls.incrementAndGet();
            }
        };

        scheduler = new DeferredEventDrainScheduler(
                deferredRepository,
                sequenceRepository,
                processor,
                kafkaTemplate,
                clock,
                meterRegistry,
                Map.of(CONSUMER, consumer),
                Duration.ofMinutes(10),
                Duration.ofSeconds(5),
                Duration.ofMinutes(1),
                50,
                "delivery.events.v1.DLT");
    }

    @Test
    void neverSkipsSequenceWhenGapRemains() {
        // last=0, deferred sequence=2 → gap at 1; scheduler must not apply 2
        DeferredIntegrationEvent held = seedDeferred(2L, T0);
        sequences.put(key(CONSUMER, "Delivery", aggregateId),
                new ConsumedAggregateSequence(CONSUMER, "Delivery", aggregateId, 0L, T0));

        scheduler.processOne(held.getId());

        assertThat(handlerCalls.get()).isZero();
        assertThat(held.getStatus()).isEqualTo(DeferredIntegrationEvent.Status.WAITING_FOR_PREDECESSOR);
        assertThat(held.getAttempts()).isEqualTo(1);
        assertThat(sequenceRepository.findCurrent(CONSUMER, "Delivery", aggregateId)).hasValue(0L);
        verify(kafkaTemplate, never()).send(any(Message.class));
    }

    @Test
    void gapWindowExpiryDeadLettersWithoutAdvancingCursor() {
        DeferredIntegrationEvent held = seedDeferred(2L, T0);
        sequences.put(key(CONSUMER, "Delivery", aggregateId),
                new ConsumedAggregateSequence(CONSUMER, "Delivery", aggregateId, 0L, T0));

        clock.advance(Duration.ofMinutes(10));
        scheduler.processOne(held.getId());

        assertThat(held.getStatus()).isEqualTo(DeferredIntegrationEvent.Status.DEAD_LETTER);
        assertThat(held.getDeadLetteredAt()).isEqualTo(clock.instant());
        assertThat(sequenceRepository.findCurrent(CONSUMER, "Delivery", aggregateId)).hasValue(0L);
        assertThat(handlerCalls.get()).isZero();
        assertThat(meterRegistry.counter(DeferredEventDrainScheduler.GAP_METRIC).count()).isEqualTo(1.0);
        verify(kafkaTemplate).send(any(Message.class));
    }

    @Test
    void drainsExactNextWhenPredecessorAlreadyApplied() {
        // last=1, deferred sequence=2 → should apply
        DeferredIntegrationEvent held = seedDeferred(2L, T0);
        sequences.put(key(CONSUMER, "Delivery", aggregateId),
                new ConsumedAggregateSequence(CONSUMER, "Delivery", aggregateId, 1L, T0));

        scheduler.processOne(held.getId());

        assertThat(handlerCalls.get()).isEqualTo(1);
        assertThat(sequenceRepository.findCurrent(CONSUMER, "Delivery", aggregateId)).hasValue(2L);
        assertThat(deferred).isEmpty();
        verify(kafkaTemplate, never()).send(any(Message.class));
    }

    private DeferredIntegrationEvent seedDeferred(long sequence, Instant receivedAt) {
        UUID eventId = UUID.randomUUID();
        String raw = write(baseEnvelope(eventId, aggregateId, sequence));
        DeferredIntegrationEvent event = DeferredIntegrationEvent.waiting(
                CONSUMER, eventId, "Delivery", aggregateId, sequence, raw, receivedAt);
        deferred.add(event);
        return event;
    }

    private ObjectNode baseEnvelope(UUID eventId, UUID aggregateId, long sequence) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("orderId", UUID.randomUUID().toString());
        payload.put("deliveryId", aggregateId.toString());
        payload.put("customerId", UUID.randomUUID().toString());
        payload.put("driverId", UUID.randomUUID().toString());
        payload.put("deliveredAt", T0.toString());

        ObjectNode root = objectMapper.createObjectNode();
        root.put("eventId", eventId.toString());
        root.put("eventType", EventContracts.DELIVERY_COMPLETED);
        root.put("eventVersion", 1);
        root.put("occurredAt", T0.toString());
        root.put("aggregateType", "Delivery");
        root.put("aggregateId", aggregateId.toString());
        root.put("aggregateSequence", sequence);
        root.set("payload", payload);
        return root;
    }

    private String write(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String key(String c, String t, UUID id) {
        return c + "|" + t + "|" + id;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
