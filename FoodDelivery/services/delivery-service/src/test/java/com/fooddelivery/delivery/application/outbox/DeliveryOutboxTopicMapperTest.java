package com.fooddelivery.delivery.application.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryOutboxTopicMapperTest {

    private final DeliveryOutboxTopicMapper mapper = new DeliveryOutboxTopicMapper();

    @Test
    void mapsKnownDeliveryEventTypes() {
        assertThat(mapper.topicFor("driver.assigned")).isEqualTo("driver.assigned");
        assertThat(mapper.topicFor("delivery.picked-up")).isEqualTo("delivery.picked-up");
        assertThat(mapper.topicFor("delivery.in-transit")).isEqualTo("delivery.in-transit");
        assertThat(mapper.topicFor("delivery.completed")).isEqualTo("delivery.completed");
        assertThat(mapper.topicFor("delivery.failed")).isEqualTo("delivery.failed");
    }

    @Test
    void rejectsUnknownEventTypes() {
        assertThatThrownBy(() -> mapper.topicFor("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported delivery event type");
    }
}
