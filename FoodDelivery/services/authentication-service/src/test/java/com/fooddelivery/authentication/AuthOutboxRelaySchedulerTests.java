package com.fooddelivery.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.authentication.infrastructure.persistence.OutboxEventRepository;
import com.fooddelivery.authentication.infrastructure.persistence.model.OutboxEvent;
import com.fooddelivery.authentication.infrastructure.scheduler.OutboxRelayScheduler;
import com.fooddelivery.commonevents.EventContracts;
import com.github.f4b6a3.uuid.UuidCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthOutboxRelaySchedulerTests {

    private OutboxEventRepository outboxEventRepository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);
        scheduler = new OutboxRelayScheduler(outboxEventRepository, kafkaTemplate, new ObjectMapper(), transactionManager);
    }

    @Test
    void relay_ShouldPublishUserRegisteredEnvelopeToAuthEvents() throws Exception {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        OutboxEvent event = new OutboxEvent(
                "User",
                userId,
                EventContracts.USER_REGISTERED,
                "{\"userId\":\"" + userId + "\",\"email\":\"new@gmail.com\"}");
        CompletableFuture future = mock(CompletableFuture.class);

        when(outboxEventRepository.findUnpublishedEventIds()).thenReturn(List.of(event.getId()));
        when(outboxEventRepository.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);
        when(future.get()).thenReturn(null);

        scheduler.relay();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(EventContracts.AUTH_EVENTS_TOPIC), eq(userId.toString()), messageCaptor.capture());
        JsonNode message = new ObjectMapper().readTree(messageCaptor.getValue());
        assertEquals(EventContracts.USER_REGISTERED, message.path("eventType").asText());
        assertEquals(userId.toString(), message.path("payload").path("userId").asText());
        assertTrue(event.isPublished());
    }
}
