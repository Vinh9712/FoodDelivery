package com.fooddelivery.order.application.outbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderOutboxTopicMapperTest {

    private final OrderOutboxTopicMapper mapper = new OrderOutboxTopicMapper();

    @Test
    void mapsKnownOrderEventTypes() {
        assertThat(mapper.topicFor("OrderCreated")).isEqualTo("order.placed");
        assertThat(mapper.topicFor("OrderCancelled")).isEqualTo("order.cancelled");
        assertThat(mapper.topicFor("DriverAssigned")).isEqualTo("driver.assigned");
        assertThat(mapper.topicFor("OrderStatusChanged")).isEqualTo("order.status-changed");
    }

    @Test
    void rejectsUnknownEventTypes() {
        assertThatThrownBy(() -> mapper.topicFor("Unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported order event type");
    }
}
