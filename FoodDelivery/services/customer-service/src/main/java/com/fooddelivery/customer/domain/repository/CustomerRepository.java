package com.fooddelivery.customer.domain.repository;

import com.fooddelivery.customer.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {
    Customer save(Customer customer);
    Optional<Customer> findById(UUID id);
    Optional<Customer> findByAuthUserId(UUID authUserId);
}
