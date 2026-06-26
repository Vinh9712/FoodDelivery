package com.fooddelivery.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.UpdateProfileCommand;
import com.fooddelivery.customer.application.usecase.impl.UpdateProfileUseCaseImpl;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import com.fooddelivery.customer.domain.repository.UserRepository;
import com.fooddelivery.customer.infrastructure.persistence.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import com.github.f4b6a3.uuid.UuidCreator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UpdateProfileUseCaseTests {

    private CustomerRepository customerRepository;
    private UserRepository userRepository;
    private OutboxEventRepository outboxEventRepository;
    private ObjectMapper objectMapper;
    private UpdateProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        userRepository = mock(UserRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        useCase = new UpdateProfileUseCaseImpl(
                customerRepository,
                userRepository,
                outboxEventRepository,
                objectMapper
        );
    }

    @Test
    void updateProfile_ShouldFail_WhenPhoneAlreadyExists() {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        User user = User.register("test@gmail.com", "0987654321", "hashed", UserRole.CUSTOMER);
        Customer customer = Customer.create(userId, "Nguyen Van A", "0987654321");

        UpdateProfileCommand command = new UpdateProfileCommand(
                userId,
                "Nguyen Van A",
                "0912345678", // New phone number
                "avatar.png"
        );

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByPhone("0912345678")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> useCase.execute(command));
        verify(customerRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_ShouldUpdateUserPhoneAndCustomerPhone_WhenPhoneIsUnique() {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        User user = User.register("test@gmail.com", "0987654321", "hashed", UserRole.CUSTOMER);
        Customer customer = Customer.create(userId, "Nguyen Van A", "0987654321");

        UpdateProfileCommand command = new UpdateProfileCommand(
                userId,
                "Nguyen Van B",
                "0912345678", // New unique phone
                "avatar.png"
        );

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByPhone("0912345678")).thenReturn(false);

        useCase.execute(command);

        assertEquals("0912345678", customer.getPhone());
        assertEquals("0912345678", user.getPhone());
        assertEquals("Nguyen Van B", customer.getFullName());

        verify(customerRepository).save(customer);
        verify(userRepository).save(user);
    }

    @Test
    void updateProfile_ShouldKeepExistingPhone_WhenPhoneIsOmitted() {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        User user = User.register("test@gmail.com", "0987654321", "hashed", UserRole.CUSTOMER);
        Customer customer = Customer.create(userId, "Nguyen Van A", "0987654321");

        UpdateProfileCommand command = new UpdateProfileCommand(
                userId,
                "Nguyen Van B",
                null, // Phone omitted
                "avatar.png"
        );

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        useCase.execute(command);

        assertEquals("0987654321", customer.getPhone());
        assertEquals("0987654321", user.getPhone());
        assertEquals("Nguyen Van B", customer.getFullName());

        verify(customerRepository).save(customer);
        verify(userRepository, never()).save(any());
    }
}
