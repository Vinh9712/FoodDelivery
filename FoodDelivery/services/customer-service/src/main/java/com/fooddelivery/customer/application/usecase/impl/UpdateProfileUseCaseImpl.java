package com.fooddelivery.customer.application.usecase.impl;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.application.command.UpdateProfileCommand;
import com.fooddelivery.customer.api.dto.response.CustomerProfileResponse;
import com.fooddelivery.customer.application.usecase.UpdateProfileUseCase;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateProfileUseCaseImpl implements UpdateProfileUseCase {

    private final CustomerRepository customerRepository;

    public UpdateProfileUseCaseImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public CustomerProfileResponse execute(UpdateProfileCommand command) {
        Customer customer = customerRepository.findByAuthUserId(command.authUserId())
                .orElseThrow(() -> new BusinessRuleException("Customer profile not found"));

        String newPhone = (command.phone() != null && !command.phone().trim().isEmpty())
                ? command.phone().trim()
                : customer.getPhone();

        customer.updateProfile(command.fullName(), newPhone, command.avatarUrl());
        customerRepository.save(customer);


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
    @Transactional
    public CustomerProfileResponse getProfile(UUID authUserId) {
        Customer customer = customerRepository.findByAuthUserId(authUserId)
                .orElseGet(() -> {
                    Customer newCustomer = Customer.create(authUserId, null, "Khách hàng", "0900000000");
                    return customerRepository.save(newCustomer);
                });

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
