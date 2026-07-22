package com.fooddelivery.order.application.outbox;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.order.domain.model.OutboxEvent;
import com.fooddelivery.order.infrastructure.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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

class OrderOutboxPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesCanonicalEnvelopeForOrderFamilyEvents() throws Exception {
        for (String eventType : new String[]{
                EventContracts.ORDER_CREATED,
                EventContracts.ORDER_STATUS_CHANGED,
                EventContracts.ORDER_CANCELLED}) {
            OutboxEventRepository repository = mock(OutboxEventRepository.class);
            KafkaTemplate<String, Object> kafkaTemplate = mock();
            UUID orderId = UUID.randomUUID();
            UUID customerId = UUID.randomUUID();
            OutboxEvent event = event(eventType, orderId, customerId, 3L);
            when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

            ArgumentCaptor<Object> envelopeCaptor = ArgumentCaptor.forClass(Object.class);
            verify(kafkaTemplate).send(
                    eq(EventContracts.ORDER_EVENTS_V1),
                    eq(orderId.toString()),
                    envelopeCaptor.capture());

            IntegrationEventEnvelope<JsonNode> envelope = deserialize(envelopeCaptor.getValue());
            assertThat(envelope.eventId()).isEqualTo(event.getId());
            assertThat(envelope.eventType()).isEqualTo(eventType);
            assertThat(envelope.eventVersion()).isEqualTo(1);
            assertThat(envelope.occurredAt()).isEqualTo(event.getCreatedAt());
            assertThat(envelope.aggregateType()).isEqualTo("Order");
            assertThat(envelope.aggregateId()).isEqualTo(orderId);
            assertThat(envelope.aggregateSequence()).isEqualTo(3L);
            assertThat(envelope.payload().path("customerId").asText()).isEqualTo(customerId.toString());
            assertThat(event.isPublished()).isTrue();
        }
    }

    @Test
    void marksEventPublishedAfterKafkaAcknowledgesIt() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = event(EventContracts.ORDER_CREATED, orderId, UUID.randomUUID(), 1L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
        verify(kafkaTemplate).send(eq(EventContracts.ORDER_EVENTS_V1),
                eq(orderId.toString()), any());
    }

    @Test
    void persistsBackoffWhenKafkaPublishFails() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.ORDER_CANCELLED, UUID.randomUUID(), UUID.randomUUID(), 1L);
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
        OutboxEvent event = event(EventContracts.ORDER_CREATED, UUID.randomUUID(), UUID.randomUUID(), 1L);
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
        OutboxEvent event = event(EventContracts.ORDER_CREATED, UUID.randomUUID(), UUID.randomUUID(), 1L);
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
        OutboxEvent event = event(EventContracts.ORDER_CREATED, UUID.randomUUID(), UUID.randomUUID(), 1L);
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

        OrderOutboxPublisher publisher = publisher(repository, kafkaTemplate, 3);
        publisher.publishOne(event.getId());
        publisher.publishOne(event.getId());

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        assertThat(event.isPublished()).isTrue();
    }

    @Test
    void secondPublisherSkipsWhenEventAlreadyPublishedAfterLock() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.ORDER_CREATED, UUID.randomUUID(), UUID.randomUUID(), 1L);

        when(repository.findByIdForUpdate(event.getId())).thenAnswer(invocation -> {
            if (!event.isPublished()) {
                event.markPublished();
            }
            return Optional.of(event);
        });

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void restoresInterruptFlagWhenKafkaWaitIsInterrupted() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.ORDER_CREATED, UUID.randomUUID(), UUID.randomUUID(), 1L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        CompletableFuture<SendResult<String, Object>> interrupted = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(interrupted);

        Thread worker = new Thread(() -> {
            Thread.currentThread().interrupt();
            publisher(repository, kafkaTemplate, 3).publishOne(event.getId());
        });
        worker.start();
        worker.join(5_000);

        assertThat(worker.isAlive()).isFalse();
        assertThat(event.isPublished()).isFalse();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Interrupted");
    }

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

    private OrderOutboxPublisher publisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, Object> kafkaTemplate,
            int maxAttempts) {
        return new OrderOutboxPublisher(
                repository,
                kafkaTemplate,
                new OrderOutboxTopicMapper(),
                objectMapper,
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                maxAttempts);
    }

    private OutboxEvent event(String eventType, UUID orderId, UUID customerId, long sequence) {
        return OutboxEvent.create(
                "Order",
                orderId,
                eventType,
                1,
                sequence,
                orderId.toString(),
                Map.of(
                        "orderId", orderId.toString(),
                        "customerId", customerId.toString(),
                        "restaurantId", UUID.randomUUID().toString()));
    }
}
