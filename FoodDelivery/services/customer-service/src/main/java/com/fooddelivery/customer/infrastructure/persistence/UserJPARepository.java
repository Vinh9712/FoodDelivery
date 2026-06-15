package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserJPARepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
}
