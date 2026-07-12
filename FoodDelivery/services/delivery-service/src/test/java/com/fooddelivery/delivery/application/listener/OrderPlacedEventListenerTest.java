package com.fooddelivery.delivery.application.listener;

import com.fooddelivery.delivery.application.listener.OrderPlacedEventListener.OrderPlacedEvent;
import com.fooddelivery.delivery.application.listener.OrderPlacedEventListener.OrderPlacedPayload;
import com.fooddelivery.delivery.application.service.DeliveryAssignmentService;
import com.fooddelivery.delivery.infrastructure.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderPlacedEventListenerTest {

    private static final String CONSUMER = "delivery-service-order-placed";

    private final DeliveryAssignmentService assignmentService = mock(DeliveryAssignmentService.class);
    private final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
    private final OrderPlacedEventListener listener = new OrderPlacedEventListener(assignmentService, processedEvents);

    @Test
    void assignsFromCompletePayloadAndMarksProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        listener.onOrderPlaced(new OrderPlacedEvent(eventId, "ORDER_PLACED",
                new OrderPlacedPayload(orderId, customerId, "123 Nguyen Trai")));

        verify(assignmentService).autoAssignDriver(orderId, customerId, "123 Nguyen Trai");
        verify(processedEvents).markProcessed(eventId, CONSUMER);
    }

    @Test
    void skipsDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        OrderPlacedEvent event = new OrderPlacedEvent(eventId, "ORDER_PLACED",
                new OrderPlacedPayload(orderId, customerId, "123 Nguyen Trai"));
        when(processedEvents.existsByEventIdAndConsumer(eventId, CONSUMER)).thenReturn(false, true);

        listener.onOrderPlaced(event);
        listener.onOrderPlaced(event);

        verify(assignmentService, times(1)).autoAssignDriver(orderId, customerId, "123 Nguyen Trai");
        verify(processedEvents, times(1)).markProcessed(eventId, CONSUMER);
    }

    @Test
    void rejectsMissingCustomerWithoutProcessingMarker() {
        UUID eventId = UUID.randomUUID();
        OrderPlacedEvent event = new OrderPlacedEvent(eventId, "ORDER_PLACED",
                new OrderPlacedPayload(UUID.randomUUID(), null, "123 Nguyen Trai"));

        assertThatThrownBy(() -> listener.onOrderPlaced(event))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(assignmentService);
        verify(processedEvents, never()).markProcessed(any(), any());
    }

    @Test
    void rejectsBlankAddressWithoutProcessingMarker() {
        UUID eventId = UUID.randomUUID();
        OrderPlacedEvent event = new OrderPlacedEvent(eventId, "ORDER_PLACED",
                new OrderPlacedPayload(UUID.randomUUID(), UUID.randomUUID(), " "));

        assertThatThrownBy(() -> listener.onOrderPlaced(event))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(assignmentService);
        verify(processedEvents, never()).markProcessed(any(), any());
    }
}
