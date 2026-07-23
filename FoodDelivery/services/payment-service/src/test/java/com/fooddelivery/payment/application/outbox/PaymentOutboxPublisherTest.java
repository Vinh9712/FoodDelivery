package com.fooddelivery.payment.application.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
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

class PaymentOutboxPublisherTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesCanonicalEnvelopeToPaymentEventsTopicWithOrderPartitionKey() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = event(EventContracts.PAYMENT_SUCCEEDED, orderId, 2L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        ArgumentCaptor<Object> envelopeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(
                eq(EventContracts.PAYMENT_EVENTS_V1),
                eq(orderId.toString()),
                envelopeCaptor.capture());

        @SuppressWarnings("unchecked")
        IntegrationEventEnvelope<JsonNode> envelope =
                (IntegrationEventEnvelope<JsonNode>) envelopeCaptor.getValue();
        assertThat(envelope.eventId()).isEqualTo(event.getId());
        assertThat(envelope.eventType()).isEqualTo(EventContracts.PAYMENT_SUCCEEDED);
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.aggregateSequence()).isEqualTo(2L);
        assertThat(envelope.aggregateId()).isEqualTo(event.getAggregateId());
        assertThat(envelope.payload().path("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void marksEventPublishedAfterKafkaAcknowledgesIt() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = event(EventContracts.PAYMENT_SUCCEEDED, orderId, 1L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
        verify(kafkaTemplate).send(eq(EventContracts.PAYMENT_EVENTS_V1),
                eq(orderId.toString()), any());
    }

    @Test
    void persistsBackoffWithoutChangingIdentityOnKafkaFailure() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        UUID orderId = UUID.randomUUID();
        OutboxEvent event = event(EventContracts.PAYMENT_FAILED, orderId, 1L);
        UUID eventId = event.getId();
        long sequence = event.getAggregateSequence();
        JsonNode payload = event.getPayload();
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(failed);
        Instant before = Instant.now();

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        assertThat(event.isPublished()).isFalse();
        assertThat(event.getId()).isEqualTo(eventId);
        assertThat(event.getAggregateSequence()).isEqualTo(sequence);
        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(event.getPartitionKey()).isEqualTo(orderId.toString());
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Kafka unavailable");
        assertThat(event.getNextAttemptAt()).isAfterOrEqualTo(before.plusSeconds(1));
        assertThat(event.isDeadLettered()).isFalse();
    }

    @Test
    void deadLettersEventAfterMaximumAttempts() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event("UnsupportedEvent", UUID.randomUUID(), 1L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        publisher(repository, kafkaTemplate, 1).publishOne(event.getId());

        assertThat(event.isDeadLettered()).isTrue();
        assertThat(event.getDeadLetteredAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void secondPublisherDoesNotPublishSameEventConcurrently() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.PAYMENT_SUCCEEDED, UUID.randomUUID(), 1L);
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

        PaymentOutboxPublisher publisher = publisher(repository, kafkaTemplate, 3);
        publisher.publishOne(event.getId());
        publisher.publishOne(event.getId());

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
        assertThat(event.isPublished()).isTrue();
    }

    @Test
    void restoresInterruptFlagWhenKafkaWaitIsInterrupted() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event(EventContracts.PAYMENT_SUCCEEDED, UUID.randomUUID(), 1L);
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        CompletableFuture<SendResult<String, Object>> neverCompletes = new CompletableFuture<>();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(neverCompletes);

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

    private PaymentOutboxPublisher publisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, Object> kafkaTemplate,
            int maxAttempts) {
        return new PaymentOutboxPublisher(
                repository,
                kafkaTemplate,
                new PaymentOutboxTopicMapper(),
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                Duration.ofMinutes(1),
                maxAttempts);
    }

    private OutboxEvent event(String eventType, UUID orderId, long sequence) {
        UUID paymentId = UUID.randomUUID();
        return new OutboxEvent(
                "Payment",
                paymentId,
                eventType,
                1,
                sequence,
                orderId.toString(),
                objectMapper.createObjectNode()
                        .put("paymentId", paymentId.toString())
                        .put("orderId", orderId.toString())
                        .put("amount", "125000")
                        .put("currency", "VND"));
    }
}
