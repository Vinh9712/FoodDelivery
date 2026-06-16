package com.fooddelivery.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.customer.infrastructure.persistence.OutboxEventRepository;
import com.fooddelivery.customer.infrastructure.persistence.model.OutboxEvent;
import com.fooddelivery.customer.infrastructure.scheduler.OutboxRelayScheduler;
import com.github.f4b6a3.uuid.UuidCreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxRelaySchedulerTests {

    private OutboxEventRepository outboxEventRepository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private ObjectMapper objectMapper;
    private PlatformTransactionManager transactionManager;
    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        objectMapper = new ObjectMapper();
        transactionManager = mock(PlatformTransactionManager.class);

        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);

        scheduler = new OutboxRelayScheduler(
                outboxEventRepository,
                kafkaTemplate,
                objectMapper,
                transactionManager
        );
    }

    @Test
    void relay_ShouldPublishToKafkaAndMarkSuccess() throws Exception {
        UUID aggregateId = UuidCreator.getTimeOrderedEpoch();
        OutboxEvent event = new OutboxEvent("Customer", aggregateId, "customer.created",
                "{\"customerId\":\"" + aggregateId + "\"}");

        when(outboxEventRepository.findUnpublishedEventIds()).thenReturn(Collections.singletonList(event.getId()));
        when(outboxEventRepository.findByIdForUpdate(event.getId())).thenReturn(java.util.Optional.of(event));

        CompletableFuture future = mock(CompletableFuture.class);
        when(future.get()).thenReturn(null);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        scheduler.relay();

        verify(kafkaTemplate, times(1)).send(eq("customer-events"), eq(aggregateId.toString()), any());
        assertTrue(event.isPublished());
        verify(outboxEventRepository, times(1)).save(event);
    }

    @Test
    void relay_ShouldRetryPublishing_WhenKafkaFails() throws Exception {
        UUID aggregateId = UuidCreator.getTimeOrderedEpoch();
        OutboxEvent event = new OutboxEvent("Customer", aggregateId, "customer.created",
                "{\"customerId\":\"" + aggregateId + "\"}");

        when(outboxEventRepository.findUnpublishedEventIds()).thenReturn(Collections.singletonList(event.getId()));
        when(outboxEventRepository.findByIdForUpdate(event.getId())).thenReturn(java.util.Optional.of(event));

        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("Kafka down"));

        scheduler.relay();

        assertFalse(event.isPublished());
        verify(outboxEventRepository, never()).save(event);
    }
}
