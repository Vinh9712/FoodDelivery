package com.fooddelivery.customer.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import com.fooddelivery.commonevents.EventContracts;
import com.fooddelivery.commonevents.UserRegisteredEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserRegisteredEventListener {

    private final ObjectMapper objectMapper;
    private final CustomerRepository customerRepository;

    public UserRegisteredEventListener(ObjectMapper objectMapper, CustomerRepository customerRepository) {
        this.objectMapper = objectMapper;
        this.customerRepository = customerRepository;
    }

    @KafkaListener(topics = EventContracts.AUTH_EVENTS_TOPIC, groupId = "customer-service")
    @Transactional
    public void handle(String message) throws Exception {
        JsonNode envelope = objectMapper.readTree(message);
        if (!EventContracts.USER_REGISTERED.equals(envelope.path("eventType").asText())) {
            return;
        }

        UserRegisteredEvent event = objectMapper.treeToValue(envelope.path("payload"), UserRegisteredEvent.class);
        if (customerRepository.findByAuthUserId(event.getUserId()).isPresent()) {
            return;
        }

        customerRepository.save(Customer.create(
                event.getUserId(),
                event.getEmail(),
                event.getFullName(),
                event.getPhone()));
    }
}
