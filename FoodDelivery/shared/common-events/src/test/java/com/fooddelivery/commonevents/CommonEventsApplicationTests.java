package com.fooddelivery.commonevents;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommonEventsApplicationTests {

    @Test
    void authEventContractIsAvailable() {
        UserRegisteredEvent event = new UserRegisteredEvent();

        assertNotNull(event);
        assertEquals(EventContracts.USER_REGISTERED, event.getEventType());
        assertEquals(EventContracts.AUTH_EVENTS_TOPIC, "auth-events");
    }
}
