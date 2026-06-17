package com.fooddelivery.customer.infrastructure.persistence;

import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class UserRepositoryAdapter implements UserRepository {
    private final UserJPARepository userJPARepository;

    public UserRepositoryAdapter(UserJPARepository userJPARepository) {
        this.userJPARepository = userJPARepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJPARepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJPARepository.existsByEmail(email);
    }

    @Override
    public boolean existsByPhone(String phone) {
        return userJPARepository.existsByPhone(phone);
    }

    @Override
    public User save(User user) {
        return userJPARepository.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userJPARepository.findById(id);
    }
}
