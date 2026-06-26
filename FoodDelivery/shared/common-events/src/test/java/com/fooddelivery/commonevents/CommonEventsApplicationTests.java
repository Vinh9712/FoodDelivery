package com.fooddelivery.commonevents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommonEventsApplicationTests {

    @Test
    void eventTypesAreInstantiable() {
        assertNotNull(new CustomerRegisteredEvent());
        assertNotNull(new CustomerUpdatedEvent());
    }
}
