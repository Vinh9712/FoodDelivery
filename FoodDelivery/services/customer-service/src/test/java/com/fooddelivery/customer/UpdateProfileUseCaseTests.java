package com.fooddelivery.customer;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.UpdateProfileCommand;
import com.fooddelivery.customer.application.usecase.impl.UpdateProfileUseCaseImpl;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
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
    private UpdateProfileUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        useCase = new UpdateProfileUseCaseImpl(customerRepository);
    }

    @Test
    void updateProfile_ShouldFail_WhenCustomerProfileNotFound() {
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        UpdateProfileCommand command = new UpdateProfileCommand(
                userId,
                "Nguyen Van A",
                "0912345678",
                "avatar.png"
        );

        when(customerRepository.findByAuthUserId(userId)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> useCase.execute(command));
        verify(customerRepository, never()).save(any());
    }

    @Test
    void updateProfile_ShouldUpdateCustomerProfile() {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        Customer customer = Customer.create(userId, "test@gmail.com", "Nguyen Van A", "0987654321");

        UpdateProfileCommand command = new UpdateProfileCommand(
                userId,
                "Nguyen Van B",
                "0912345678",
                "avatar.png"
        );

        when(customerRepository.findByAuthUserId(userId)).thenReturn(Optional.of(customer));

        var response = useCase.execute(command);

        assertEquals("0912345678", customer.getPhone());
        assertEquals("Nguyen Van B", customer.getFullName());
        assertEquals("test@gmail.com", response.email());

        verify(customerRepository).save(customer);
    }

    @Test
    void updateProfile_ShouldKeepExistingPhone_WhenPhoneIsOmitted() {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        Customer customer = Customer.create(userId, "test@gmail.com", "Nguyen Van A", "0987654321");

        UpdateProfileCommand command = new UpdateProfileCommand(
                userId,
                "Nguyen Van B",
                null, // Phone omitted
                "avatar.png"
        );

        when(customerRepository.findByAuthUserId(userId)).thenReturn(Optional.of(customer));

        useCase.execute(command);

        assertEquals("0987654321", customer.getPhone());
        assertEquals("Nguyen Van B", customer.getFullName());

        verify(customerRepository).save(customer);
    }
}
