package com.fooddelivery.order.application.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;

/**
 * Named consumer that can apply domain effects for a sequenced inbox event.
 */
public interface SequencedConsumer {

    String consumerName();

    void handle(IntegrationEventEnvelope<JsonNode> envelope) throws Exception;
}
