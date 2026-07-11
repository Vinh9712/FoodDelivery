package com.fooddelivery.notification;

import com.fooddelivery.notification.api.dto.NotificationRequest;
import com.fooddelivery.notification.application.NotificationJobService;
import com.fooddelivery.notification.application.dispatch.NotificationDispatchGateway;
import com.fooddelivery.notification.application.dispatch.NotificationJobProcessor;
import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.domain.model.valueobject.NotificationStatus;
import com.fooddelivery.notification.infrastructure.repository.NotificationRepository;
import com.fooddelivery.notification.infrastructure.messaging.OrderEventListener;
import com.fooddelivery.notification.infrastructure.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.UUID;
import java.util.Map;

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
    private OrderEventListener orderEventListener;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

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
    void duplicateKafkaEventCreatesOnePersistentJob() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Map<String, Object> event = Map.of(
                "eventId", eventId.toString(),
                "payload", Map.of(
                        "orderId", orderId.toString(),
                        "customerId", customerId.toString()));

        orderEventListener.onPaymentProcessed(event);
        orderEventListener.onPaymentProcessed(event);

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    private NotificationRequest request() {
        return new NotificationRequest(
                UUID.randomUUID(), UUID.randomUUID(), "IN_APP",
                "Order confirmed", "Your order is being prepared");
    }
}
