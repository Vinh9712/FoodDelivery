package com.fooddelivery.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.RegisterCustomerCommand;
import com.fooddelivery.customer.application.usecase.impl.RegisterCustomerUseCaseImpl;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import com.fooddelivery.customer.domain.repository.UserRepository;
import com.fooddelivery.customer.infrastructure.persistence.OutboxEventRepository;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegisterCustomerUseCaseTests {

    private UserRepository userRepository;
    private CustomerRepository customerRepository;
    private OutboxEventRepository outboxEventRepository;
    private PasswordEncoder passwordEncoder;
    private ObjectMapper objectMapper;
    private RegisterCustomerUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        customerRepository = mock(CustomerRepository.class);
        outboxEventRepository = mock(OutboxEventRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        useCase = new RegisterCustomerUseCaseImpl(
                userRepository,
                customerRepository,
                outboxEventRepository,
                passwordEncoder,
                objectMapper);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void register_ShouldFail_WhenEmailAlreadyExists() {
        RegisterCustomerCommand command = new RegisterCustomerCommand(
                "duplicate@gmail.com",
                "0987654321",
                "password123",
                "Nguyen Van A",
                UserRole.CUSTOMER);

        when(userRepository.existsByEmail(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> useCase.execute(command));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_ShouldFail_WhenPhoneAlreadyExists() {
        RegisterCustomerCommand command = new RegisterCustomerCommand(
                "new@gmail.com",
                "duplicatephone",
                "password123",
                "Nguyen Van A",
                UserRole.CUSTOMER);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> useCase.execute(command));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_ShouldCreateUserWithCustomerRoleOnly() throws Exception {
        UUID userId = com.github.f4b6a3.uuid.UuidCreator.getTimeOrderedEpoch();
        RegisterCustomerCommand command = new RegisterCustomerCommand(
                "new@gmail.com",
                "0987654321",
                "password123",
                "Nguyen Van A",
                UserRole.ADMIN); // Command requests ADMIN role

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setPrivateField(user, "id", userId);
            return user;
        });
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(command);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(UserRole.CUSTOMER, userCaptor.getValue().getRole());
    }
}
