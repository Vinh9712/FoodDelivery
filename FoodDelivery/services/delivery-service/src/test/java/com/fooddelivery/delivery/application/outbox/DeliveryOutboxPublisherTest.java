package com.fooddelivery.delivery.application.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryOutboxPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesCanonicalEnvelopeForLifecycleEvents() throws Exception {
        for (String eventType : new String[]{
                EventContracts.DRIVER_ASSIGNED,
                EventContracts.DELIVERY_PICKED_UP,
                EventContracts.DELIVERY_IN_TRANSIT,
                EventContracts.DELIVERY_COMPLETED,
                EventContracts.DELIVERY_FAILED}) {
            OutboxEventRepository repository = mock(OutboxEventRepository.class);
            KafkaTemplate<String, Object> kafkaTemplate = mock();
            UUID orderId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            OutboxEvent event = event(eventType, orderId, customerId, 4L);
            when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

            ArgumentCaptor<Object> envelopeCaptor = ArgumentCaptor.forClass(Object.class);
            verify(kafkaTemplate).send(
                    eq(EventContracts.DELIVERY_EVENTS_V1),
                    eq(orderId.toString()),
                    envelopeCaptor.capture());

            IntegrationEventEnvelope<JsonNode> envelope = deserialize(envelopeCaptor.getValue());
            assertThat(envelope.eventId()).isEqualTo(event.getId());
            assertThat(envelope.eventType()).isEqualTo(eventType);
            assertThat(envelope.eventVersion()).isEqualTo(1);
            assertThat(envelope.occurredAt()).isEqualTo(event.getOccurredAt());
            assertThat(envelope.aggregateType()).isEqualTo("Delivery");
            assertThat(envelope.aggregateId()).isEqualTo(event.getAggregateId());
            assertThat(envelope.aggregateSequence()).isEqualTo(4L);
            assertThat(envelope.payload().path("customerId").asText()).isEqualTo(customerId.toString());
            assertThat(envelope.payload().path("orderId").asText()).isEqualTo(orderId.toString());
            assertThat(event.isPublished()).isTrue();
        }
    }

    @Test
    void marksEventPublishedAfterKafkaAcknowledgesIt() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = event(EventContracts.DRIVER_ASSIGNED, orderId, UUID.randomUUID(), 1L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
        verify(kafkaTemplate).send(eq(EventContracts.DELIVERY_EVENTS_V1),
                eq(orderId.toString()), any());
    }

    @Test
    void persistsBackoffWhenKafkaPublishFails() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.DELIVERY_FAILED, UUID.randomUUID(), UUID.randomUUID(), 2L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);
        Instant before = Instant.now();

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        assertThat(event.isPublished()).isFalse();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Kafka unavailable");
        assertThat(event.getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(1));
        assertThat(event.isDeadLettered()).isFalse();
    }

    @Test
    void doesNotPublishWhenRetryNotDueYet() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.DELIVERY_PICKED_UP, UUID.randomUUID(), UUID.randomUUID(), 1L);
        event.recordFailure("previous failure", Instant.now().plusSeconds(60));
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        assertThat(event.isPublished()).isFalse();
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void deadLettersEventAfterMaximumAttempts() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event("UnsupportedEvent", UUID.randomUUID(), UUID.randomUUID(), 1L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        publisher(repository, kafkaTemplate, 1).publishOne(event.getId());

        assertThat(event.isDeadLettered()).isTrue();
        assertThat(event.getDeadLetteredAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void doesNotRepublishAlreadyPublishedEvent() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.DRIVER_ASSIGNED, UUID.randomUUID(), UUID.randomUUID(), 1L);
        event.markPublished();
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void secondPublisherDoesNotPublishSameEventConcurrently() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.DRIVER_ASSIGNED, UUID.randomUUID(), UUID.randomUUID(), 1L);
        AtomicInteger lockCalls = new AtomicInteger();

        when(repository.findByIdForUpdate(event.getId())).thenAnswer(invocation -> {
            int call = lockCalls.incrementAndGet();
            if (call == 1) {
                return Optional.of(event);
            }
            return Optional.empty();
        });
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenAnswer(invocation -> {
            event.markPublished();
            return CompletableFuture.completedFuture(null);
        });

        DeliveryOutboxPublisher publisher = publisher(repository, kafkaTemplate, 3);
        publisher.publishOne(event.getId());
        publisher.publishOne(event.getId());

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        assertThat(event.isPublished()).isTrue();
    }

    @Test
    void mapsLifecycleEventTypesToDeliveryFamilyTopic() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        for (String eventType : new String[]{
                EventContracts.DRIVER_ASSIGNED,
                EventContracts.DELIVERY_PICKED_UP,
                EventContracts.DELIVERY_IN_TRANSIT,
                EventContracts.DELIVERY_COMPLETED,
                EventContracts.DELIVERY_FAILED}) {
            UUID orderId = UUID.randomUUID();
            OutboxEvent event = event(eventType, orderId, UUID.randomUUID(), 1L);
            when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

            verify(kafkaTemplate).send(
                    eq(EventContracts.DELIVERY_EVENTS_V1),
                    eq(orderId.toString()),
                    any());
        }
    }

    @SuppressWarnings("unchecked")
    private IntegrationEventEnvelope<JsonNode> deserialize(Object value) throws Exception {
        if (value instanceof IntegrationEventEnvelope<?> envelope) {
            JsonNode payload = objectMapper.valueToTree(envelope.payload());
            return new IntegrationEventEnvelope<>(
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.eventVersion(),
                    envelope.occurredAt(),
                    envelope.aggregateType(),
                    envelope.aggregateId(),
                    envelope.aggregateSequence(),
                    payload);
        }
        String json = value instanceof String s ? s : objectMapper.writeValueAsString(value);
        JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(IntegrationEventEnvelope.class, JsonNode.class);
        return objectMapper.readValue(json, type);
    }

    private DeliveryOutboxPublisher publisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, Object> kafkaTemplate,
            int maxAttempts) {
        return new DeliveryOutboxPublisher(
                repository,
                kafkaTemplate,
                new DeliveryOutboxTopicMapper(),
                objectMapper,
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                maxAttempts);
    }

    private OutboxEvent event(String eventType, UUID orderId, UUID customerId, long sequence) {
        UUID deliveryId = UUID.randomUUID();
        String payload = """
                {"orderId":"%s","deliveryId":"%s","customerId":"%s"}
                """.formatted(orderId, deliveryId, customerId).trim();
        return new OutboxEvent(
                "Delivery",
                deliveryId,
                eventType,
                1,
                sequence,
                orderId.toString(),
                payload);
    }
}
