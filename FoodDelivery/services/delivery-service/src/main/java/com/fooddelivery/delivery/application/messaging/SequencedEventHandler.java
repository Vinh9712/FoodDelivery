package com.fooddelivery.delivery.application.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fooddelivery.commonevents.IntegrationEventEnvelope;

/**
 * Transactional domain handler invoked only for the next contiguous sequence.
 */
@FunctionalInterface
public interface SequencedEventHandler {

    void apply(IntegrationEventEnvelope<JsonNode> envelope) throws Exception;
}
