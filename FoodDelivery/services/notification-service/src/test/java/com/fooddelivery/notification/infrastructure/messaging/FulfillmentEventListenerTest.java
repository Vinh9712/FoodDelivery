package com.fooddelivery.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;
import com.fooddelivery.notification.api.dto.NotificationRequest;
import com.fooddelivery.notification.application.NotificationJobService;
import com.fooddelivery.notification.domain.model.Notification;
import com.fooddelivery.notification.domain.model.valueobject.Channel;
import com.fooddelivery.notification.domain.model.valueobject.EntityReference;
import com.fooddelivery.notification.domain.model.valueobject.RenderedContent;
import com.fooddelivery.notification.infrastructure.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentEventListenerTest {

    @Mock
    private NotificationJobService notificationJobService;
    @Mock
    private ProcessedEventRepository processedEventRepository;

    private FulfillmentEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        listener = new FulfillmentEventListener(notificationJobService, processedEventRepository, objectMapper);
    }

    @Test
    void orderStatusChangedEnqueuesJob() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(processedEventRepository.existsByEventIdAndConsumer(eventId, FulfillmentEventListener.CONSUMER_NAME))
                .thenReturn(false);
        when(notificationJobService.enqueue(any())).thenReturn(sampleJob(customerId, orderId));

        listener.onFulfillmentEvent(envelope(
                eventId, EventContracts.ORDER_STATUS_CHANGED, orderId, customerId,
                objectMapper.createObjectNode()
                        .put("orderId", orderId.toString())
                        .put("customerId", customerId.toString())
                        .put("toStatus", "PREPARING")));

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationJobService).enqueue(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("Order update");
        assertThat(captor.getValue().message()).contains("PREPARING");
        verify(processedEventRepository).markProcessed(eventId, FulfillmentEventListener.CONSUMER_NAME);
    }

    @Test
    void duplicateEventIsSkipped() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsByEventIdAndConsumer(eventId, FulfillmentEventListener.CONSUMER_NAME))
                .thenReturn(true);

        listener.onFulfillmentEvent(envelope(
                eventId, EventContracts.DRIVER_ASSIGNED, UUID.randomUUID(), UUID.randomUUID(),
                objectMapper.createObjectNode()));

        verify(notificationJobService, never()).enqueue(any());
        verify(processedEventRepository, never()).markProcessed(any(), any());
    }

    private String envelope(
            UUID eventId, String type, UUID orderId, UUID customerId, ObjectNode payload) throws Exception {
        if (!payload.has("orderId")) {
            payload.put("orderId", orderId.toString());
        }
        if (!payload.has("customerId")) {
            payload.put("customerId", customerId.toString());
        }
        IntegrationEventEnvelope<ObjectNode> env = new IntegrationEventEnvelope<>(
                eventId, type, 1, Instant.parse("2026-07-23T10:00:00Z"),
                "Order", orderId, 1L, payload);
        return objectMapper.writeValueAsString(env);
    }

    private Notification sampleJob(UUID customerId, UUID orderId) {
        return Notification.create(
                "k", customerId, "ORDER_NOTIFICATION", Channel.IN_APP,
                new RenderedContent("t", "b"), new EntityReference("Order", orderId), null);
    }
}
