package com.fooddelivery.order.application.outbox;

import com.fooddelivery.commonevents.EventContracts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderOutboxTopicMapperTest {

    private final OrderOutboxTopicMapper mapper = new OrderOutboxTopicMapper();

    @Test
    void mapsKnownOrderEventTypesToFamilyTopic() {
        assertThat(mapper.topicFor(EventContracts.ORDER_CREATED))
                .isEqualTo(EventContracts.ORDER_EVENTS_V1);
        assertThat(mapper.topicFor(EventContracts.ORDER_CANCELLED))
                .isEqualTo(EventContracts.ORDER_EVENTS_V1);
        assertThat(mapper.topicFor(EventContracts.ORDER_STATUS_CHANGED))
                .isEqualTo(EventContracts.ORDER_EVENTS_V1);
    }

    @Test
    void neverMapsDriverAssignedFromOrderService() {
        assertThatThrownBy(() -> mapper.topicFor(EventContracts.DRIVER_ASSIGNED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported order event type");
    }

    @Test
    void rejectsUnknownEventTypes() {
        assertThatThrownBy(() -> mapper.topicFor("Unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported order event type");
    }
}
