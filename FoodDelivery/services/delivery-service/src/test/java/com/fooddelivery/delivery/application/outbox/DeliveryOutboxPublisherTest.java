package com.fooddelivery.delivery.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void marksEventPublishedAfterKafkaAcknowledgesIt() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event("driver.assigned");
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
        verify(kafkaTemplate).send(eq("driver.assigned"),
                eq(event.getAggregateId().toString()), any());
    }

    @Test
    void persistsBackoffWhenKafkaPublishFails() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event("delivery.failed");
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
        OutboxEvent event = event("delivery.picked-up");
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
        OutboxEvent event = event("UnsupportedEvent");
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
        OutboxEvent event = event("driver.assigned");
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
        OutboxEvent event = event("driver.assigned");
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
    void mapsLifecycleEventTypesToTopics() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        for (String eventType : new String[]{
                "delivery.picked-up", "delivery.in-transit", "delivery.completed", "delivery.failed"}) {
            OutboxEvent event = event(eventType);
            when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
            when(kafkaTemplate.send(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(null));

            publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

            verify(kafkaTemplate).send(eq(eventType), eq(event.getAggregateId().toString()), any());
        }
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

    private OutboxEvent event(String eventType) {
        return new OutboxEvent(
                "Delivery",
                UUID.randomUUID(),
                eventType,
                "{\"orderId\":\"" + UUID.randomUUID() + "\"}");
    }
}
