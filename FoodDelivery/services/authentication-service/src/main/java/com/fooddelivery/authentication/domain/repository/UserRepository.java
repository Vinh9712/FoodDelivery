package com.fooddelivery.authentication.domain.repository;

import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.enums.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    User save(User user);

    Optional<User> findById(UUID id);

    List<User> findAll(int page, int size, String search, UserRole role, Boolean active);

    long count(String search, UserRole role, Boolean active);

    long countByRole(UserRole role);
}
