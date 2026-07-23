package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerJPARepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {
    Optional<Customer> findByAuthUserId(UUID authUserId);

    @Modifying
    @Query("UPDATE Address a SET a.defaultAddress = false WHERE a.customer.id = :customerId AND a.defaultAddress = true")
    void unsetDefaultAddresses(@Param("customerId") UUID customerId);
}
