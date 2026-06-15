package com.fooddelivery.customer.domain.repository;

import com.fooddelivery.customer.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

}
