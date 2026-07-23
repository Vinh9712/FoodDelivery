package com.fooddelivery.authentication.application.usecase.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.authentication.api.dto.response.UserRegistrationResponse;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.authentication.application.command.RegisterCustomerCommand;
import com.fooddelivery.authentication.application.usecase.RegisterCustomerUseCase;
import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import com.fooddelivery.authentication.domain.repository.UserRepository;
import com.fooddelivery.authentication.infrastructure.persistence.OutboxEventRepository;
import com.fooddelivery.authentication.infrastructure.persistence.model.OutboxEvent;
import com.fooddelivery.commonevents.UserRegisteredEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterCustomerUseCaseImpl implements RegisterCustomerUseCase {

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public RegisterCustomerUseCaseImpl(
            UserRepository userRepository,
            OutboxEventRepository outboxEventRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public UserRegistrationResponse execute(RegisterCustomerCommand command) {
        UserRole role = command.role() != null ? command.role() : UserRole.CUSTOMER;
        if (role == UserRole.ADMIN) {
            throw new BusinessRuleException("Cannot self-register as ADMIN");
        }

        if (userRepository.existsByEmail(command.email().trim().toLowerCase())) {
            throw new BusinessRuleException("Email already exists");
        }

        if (userRepository.existsByPhone(command.phone().trim())) {
            throw new BusinessRuleException("Phone number already exists");
        }

        String passwordHash = passwordEncoder.encode(command.password());
        User user = User.register(command.email(), command.phone(), passwordHash, role);
        user = userRepository.save(user);

        try {
            UserRegisteredEvent event = new UserRegisteredEvent(
                    user.getId(),
                    user.getEmail(),
                    user.getPhone(),
                    command.fullName(),
                    user.getRole().name()
            );
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                    "User",
                    user.getId(),
                    event.getEventType(),
                    payload
            );
            outboxEventRepository.save(outboxEvent);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize outbox event", ex);
        }

        return new UserRegistrationResponse(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                command.fullName(),
                user.getRole().name()
        );
    }
}
