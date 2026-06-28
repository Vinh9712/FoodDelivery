package com.fooddelivery.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import com.fooddelivery.customer.infrastructure.messaging.UserRegisteredEventListener;
import com.fooddelivery.commonevents.UserRegisteredEvent;
import com.github.f4b6a3.uuid.UuidCreator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserRegisteredEventListenerTests {

    private ObjectMapper objectMapper;
    private CustomerRepository customerRepository;
    private UserRegisteredEventListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        customerRepository = mock(CustomerRepository.class);
        listener = new UserRegisteredEventListener(objectMapper, customerRepository);
    }

    @Test
    void handle_ShouldCreateCustomer_WhenUserRegisteredEventArrives() throws Exception {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserRegisteredEvent event = new UserRegisteredEvent(
                userId,
                "new@gmail.com",
                "0987654321",
                "Nguyen Van A",
                "CUSTOMER");
        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "user.registered",
                "payload", event));

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.empty());

        listener.handle(message);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        assertEquals(userId, customerCaptor.getValue().getUserId());
        assertEquals("new@gmail.com", customerCaptor.getValue().getEmail());
        assertEquals("Nguyen Van A", customerCaptor.getValue().getFullName());
    }

    @Test
    void handle_ShouldIgnore_WhenCustomerAlreadyExists() throws Exception {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        UserRegisteredEvent event = new UserRegisteredEvent(
                userId,
                "new@gmail.com",
                "0987654321",
                "Nguyen Van A",
                "CUSTOMER");
        String message = objectMapper.writeValueAsString(Map.of(
                "eventType", "user.registered",
                "payload", event));

        when(customerRepository.findByUserId(userId))
                .thenReturn(Optional.of(Customer.create(userId, "new@gmail.com", "Nguyen Van A", "0987654321")));

        listener.handle(message);

        verify(customerRepository, never()).save(any());
    }
}
