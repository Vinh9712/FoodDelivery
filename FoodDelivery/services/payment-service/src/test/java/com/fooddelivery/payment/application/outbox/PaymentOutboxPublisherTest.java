package com.fooddelivery.payment.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.payment.infrastructure.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOutboxPublisherTest {

    @Test
    void marksEventPublishedAfterKafkaAcknowledgesIt() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event("PaymentSucceeded");
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher(repository, kafkaTemplate, 3).publishOne(event.getId());

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
        verify(kafkaTemplate).send(eq("payment.processed"),
                eq(event.getAggregateId().toString()), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistsBackoffWhenKafkaPublishFails() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event("PaymentFailed");
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
    void deadLettersEventAfterMaximumAttempts() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaTemplate<String, Object> kafkaTemplate = mock();
        OutboxEvent event = event("UnsupportedEvent");
        when(repository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));

        publisher(repository, kafkaTemplate, 1).publishOne(event.getId());

        assertThat(event.isDeadLettered()).isTrue();
        assertThat(event.getDeadLetteredAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(1);
    }

    private PaymentOutboxPublisher publisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, Object> kafkaTemplate,
            int maxAttempts) {
        return new PaymentOutboxPublisher(
                repository, kafkaTemplate, Duration.ofSeconds(2), Duration.ofSeconds(1),
                Duration.ofMinutes(1), maxAttempts);
    }

    private OutboxEvent event(String eventType) {
        return new OutboxEvent(
                "Payment", UUID.randomUUID(), eventType,
                new ObjectMapper().createObjectNode().put("orderId", UUID.randomUUID().toString()));
    }
}
