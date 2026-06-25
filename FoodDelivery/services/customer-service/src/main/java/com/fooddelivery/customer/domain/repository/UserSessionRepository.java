package com.fooddelivery.customer.domain.repository;

import com.fooddelivery.customer.domain.model.UserSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository {
    UserSession save(UserSession session);

    Optional<UserSession> findById(UUID id);

    List<UserSession> findAllByUserId(UUID userId);

    void markNotCurrentByUserId(UUID userId);
}
