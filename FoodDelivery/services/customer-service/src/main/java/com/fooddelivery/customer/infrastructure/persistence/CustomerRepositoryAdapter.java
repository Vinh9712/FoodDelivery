package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CustomerRepositoryAdapter implements CustomerRepository {
    private final CustomerJPARepository customerJPARepository;

    public CustomerRepositoryAdapter(CustomerJPARepository customerJPARepository) {
        this.customerJPARepository = customerJPARepository;
    }

    @Override
    public Customer save(Customer customer) {
        return customerJPARepository.save(customer);
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return customerJPARepository.findById(id);
    }

    @Override
    public Optional<Customer> findByUserId(UUID userId) {
        return customerJPARepository.findByUserId(userId);
    }
}
