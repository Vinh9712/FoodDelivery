package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.repository.UserRepository;

import java.util.Optional;

public class UserRepositoryAdapter implements UserRepository {
    private final UserRepository userRepository;

    public UserRepositoryAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return userRepository.existsByPhone(phone);
    }

}
