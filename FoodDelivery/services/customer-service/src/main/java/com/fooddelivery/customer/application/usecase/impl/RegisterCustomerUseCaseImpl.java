package com.fooddelivery.customer.application.usecase.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.RegisterCustomerCommand;
import com.fooddelivery.customer.api.dto.response.CustomerProfileResponse;
import com.fooddelivery.customer.application.usecase.RegisterCustomerUseCase;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import com.fooddelivery.customer.domain.repository.UserRepository;
import com.fooddelivery.customer.infrastructure.persistence.OutboxEventRepository;
import com.fooddelivery.customer.infrastructure.persistence.model.OutboxEvent;
import com.fooddelivery.commonevents.CustomerRegisteredEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterCustomerUseCaseImpl implements RegisterCustomerUseCase {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public RegisterCustomerUseCaseImpl(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            OutboxEventRepository outboxEventRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CustomerProfileResponse execute(RegisterCustomerCommand command) {
        if (userRepository.existsByEmail(command.email().trim().toLowerCase())) {
            throw new BusinessRuleException("Email already exists");
        }

        if (userRepository.existsByPhone(command.phone().trim())) {
            throw new BusinessRuleException("Phone number already exists");
        }

        String passwordHash = passwordEncoder.encode(command.password());
        User user = User.register(command.email(), command.phone(), passwordHash, UserRole.CUSTOMER);
        user = userRepository.save(user);

        Customer customer = null;
        customer = Customer.create(user.getId(), command.fullName(), command.phone());
        customer = customerRepository.save(customer);

        try {
            CustomerRegisteredEvent event = new CustomerRegisteredEvent(
                    customer != null ? customer.getId() : user.getId(),
                    user.getId(),
                    user.getEmail(),
                    user.getPhone(),
                    command.fullName(),
                    user.getRole().name()
            );
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                    "Customer",
                    user.getId(),
                    event.getEventType(),
                    payload
            );
            outboxEventRepository.save(outboxEvent);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize outbox event", ex);
        }

        return new CustomerProfileResponse(
                customer != null ? customer.getId() : null,
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                command.fullName(),
                customer != null ? customer.getAvatarUrl() : null,
                customer != null ? customer.getCustomerType().name() : null,
                customer != null ? customer.getLoyaltyPoints() : 0
        );
    }
}
