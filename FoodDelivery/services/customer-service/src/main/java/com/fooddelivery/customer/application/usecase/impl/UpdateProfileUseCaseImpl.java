package com.fooddelivery.customer.application.usecase.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.UpdateProfileCommand;
import com.fooddelivery.customer.api.dto.response.CustomerProfileResponse;
import com.fooddelivery.customer.application.usecase.UpdateProfileUseCase;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import com.fooddelivery.customer.infrastructure.persistence.OutboxEventRepository;
import com.fooddelivery.customer.infrastructure.persistence.model.OutboxEvent;
import com.fooddelivery.commonevents.CustomerUpdatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class UpdateProfileUseCaseImpl implements UpdateProfileUseCase {

    private final CustomerRepository customerRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public UpdateProfileUseCaseImpl(
            CustomerRepository customerRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.customerRepository = customerRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CustomerProfileResponse execute(UpdateProfileCommand command) {
        Customer customer = customerRepository.findByAuthUserId(command.authUserId())
                .orElseThrow(() -> new BusinessRuleException("Customer profile not found"));

        String newPhone = (command.phone() != null && !command.phone().trim().isEmpty())
                ? command.phone().trim()
                : customer.getPhone();

        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(customer.getFullName(), command.fullName())) {
            changedFields.add("fullName");
        }
        if (!Objects.equals(customer.getPhone(), newPhone)) {
            changedFields.add("phone");
        }
        if (!Objects.equals(customer.getAvatarUrl(), command.avatarUrl())) {
            changedFields.add("avatarUrl");
        }

        customer.updateProfile(command.fullName(), newPhone, command.avatarUrl());
        customerRepository.save(customer);

        if (!changedFields.isEmpty()) {
            try {
                CustomerUpdatedEvent event = new CustomerUpdatedEvent(
                        customer.getId(),
                        changedFields,
                        customer.getFullName(),
                        customer.getPhone(),
                        customer.getAvatarUrl()
                );
                String payload = objectMapper.writeValueAsString(event);
                OutboxEvent outboxEvent = new OutboxEvent(
                        "Customer",
                        customer.getAuthUserId(),
                        event.getEventType(),
                        payload
                );
                outboxEventRepository.save(outboxEvent);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to serialize outbox event", ex);
            }
        }

        return new CustomerProfileResponse(
                customer.getId(),
                customer.getAuthUserId(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getFullName(),
                customer.getAvatarUrl(),
                customer.getCustomerType().name(),
                customer.getLoyaltyPoints()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(UUID authUserId) {
        Customer customer = customerRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new BusinessRuleException("Customer profile not found"));

        return new CustomerProfileResponse(
                customer.getId(),
                customer.getAuthUserId(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getFullName(),
                customer.getAvatarUrl(),
                customer.getCustomerType().name(),
                customer.getLoyaltyPoints()
        );
    }
}
