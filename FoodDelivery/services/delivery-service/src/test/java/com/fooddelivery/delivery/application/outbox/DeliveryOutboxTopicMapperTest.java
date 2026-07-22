package com.fooddelivery.delivery.application.outbox;

import com.fooddelivery.commonevents.EventContracts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryOutboxTopicMapperTest {

    private final DeliveryOutboxTopicMapper mapper = new DeliveryOutboxTopicMapper();

    @Test
    void mapsKnownDeliveryEventTypesToFamilyTopic() {
        assertThat(mapper.topicFor(EventContracts.DRIVER_ASSIGNED))
                .isEqualTo(EventContracts.DELIVERY_EVENTS_V1);
        assertThat(mapper.topicFor(EventContracts.DELIVERY_PICKED_UP))
                .isEqualTo(EventContracts.DELIVERY_EVENTS_V1);
        assertThat(mapper.topicFor(EventContracts.DELIVERY_IN_TRANSIT))
                .isEqualTo(EventContracts.DELIVERY_EVENTS_V1);
        assertThat(mapper.topicFor(EventContracts.DELIVERY_COMPLETED))
                .isEqualTo(EventContracts.DELIVERY_EVENTS_V1);
        assertThat(mapper.topicFor(EventContracts.DELIVERY_FAILED))
                .isEqualTo(EventContracts.DELIVERY_EVENTS_V1);
    }

    @Test
    void rejectsUnknownEventTypes() {
        assertThatThrownBy(() -> mapper.topicFor("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported delivery event type");
    }
}
