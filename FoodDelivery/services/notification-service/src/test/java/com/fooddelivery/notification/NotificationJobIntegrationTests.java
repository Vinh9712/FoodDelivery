package com.fooddelivery.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.notification.api.dto.NotificationRequest;
import com.fooddelivery.notification.application.NotificationJobService;
import com.fooddelivery.notification.application.dispatch.NotificationDispatchGateway;
import com.fooddelivery.notification.application.dispatch.NotificationJobProcessor;
import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.domain.model.valueobject.NotificationStatus;
import com.fooddelivery.notification.infrastructure.messaging.FulfillmentEventListener;
import com.fooddelivery.notification.infrastructure.repository.NotificationRepository;
import com.fooddelivery.notification.infrastructure.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

@SpringBootTest
@Import(NotificationJobIntegrationTests.TestConfig.class)
class NotificationJobIntegrationTests {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        NotificationDispatchGateway notificationDispatchGateway() {
            return mock(NotificationDispatchGateway.class);
        }
    }

    @Autowired
    private NotificationJobService notificationJobService;

    @Autowired
    private NotificationJobProcessor notificationJobProcessor;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationDispatchGateway dispatchGateway;

    @Autowired
    private FulfillmentEventListener fulfillmentEventListener;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        processedEventRepository.deleteAll();
        notificationRepository.deleteAll();
        reset(dispatchGateway);
    }

    @Test
    void enqueueIsPersistentIdempotentAndDispatchesSuccessfully() {
        NotificationRequest request = request();

        Notification first = notificationJobService.enqueue(request);
        Notification duplicate = notificationJobService.enqueue(request);
        notificationJobProcessor.processOne(first.getId());

        Notification sent = notificationRepository.findById(first.getId()).orElseThrow();
        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.getSentAt()).isNotNull();
    }

    @Test
    void failedDispatchRetriesThenMovesToDeadLetter() {
        Notification notification = notificationJobService.enqueue(request());
        doThrow(new IllegalStateException("Provider unavailable"))
                .when(dispatchGateway).send(org.mockito.ArgumentMatchers.any());

        notificationJobProcessor.processOne(notification.getId());
        Notification retry = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(retry.getStatus()).isEqualTo(NotificationStatus.RETRY_SCHEDULED);
        assertThat(retry.getRetryCount()).isEqualTo(1);
        assertThat(retry.getLastError()).contains("Provider unavailable");

        notificationJobProcessor.processOne(notification.getId());
        Notification deadLetter = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(deadLetter.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTER);
        assertThat(deadLetter.getRetryCount()).isEqualTo(2);
        assertThat(deadLetter.getFailedAt()).isNotNull();
    }

    @Test
    void duplicateKafkaFamilyEventCreatesOnePersistentJob() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = envelopeJson(eventId, EventContracts.PAYMENT_SUCCEEDED, orderId, customerId);

        fulfillmentEventListener.onFulfillmentEvent(json);
        fulfillmentEventListener.onFulfillmentEvent(json);

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
        Notification job = notificationRepository.findAll().getFirst();
        assertThat(job.getTitle()).isEqualTo("Payment completed");
        assertThat(job.getUserId()).isEqualTo(customerId);
        assertThat(job.getEntityId()).isEqualTo(orderId);
    }

    private String envelopeJson(UUID eventId, String eventType, UUID orderId, UUID customerId) throws Exception {
        ObjectNode payload = objectMapper.createObjectNode()
                .put("orderId", orderId.toString())
                .put("customerId", customerId.toString())
                .put("paymentId", UUID.randomUUID().toString())
                .put("amount", "10000")
                .put("currency", "VND")
                .put("paidAt", Instant.parse("2026-07-23T10:00:00Z").toString());
        IntegrationEventEnvelope<ObjectNode> envelope = new IntegrationEventEnvelope<>(
                eventId,
                eventType,
                1,
                Instant.parse("2026-07-23T10:00:00Z"),
                "Payment",
                orderId,
                1L,
                payload);
        return objectMapper.writeValueAsString(envelope);
    }

    private NotificationRequest request() {
        return new NotificationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "IN_APP",
                "Order confirmed", "Your order is being prepared");
    }
}
