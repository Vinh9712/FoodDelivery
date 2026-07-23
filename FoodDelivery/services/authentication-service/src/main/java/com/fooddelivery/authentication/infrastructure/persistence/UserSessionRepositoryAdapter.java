package com.fooddelivery.authentication.infrastructure.persistence;

import com.fooddelivery.authentication.domain.model.UserSession;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserSessionRepositoryAdapter implements UserSessionRepository {

    private final UserSessionJPARepository userSessionJPARepository;

    public UserSessionRepositoryAdapter(UserSessionJPARepository userSessionJPARepository) {
        this.userSessionJPARepository = userSessionJPARepository;
    }

    @Override
    public UserSession save(UserSession session) {
        return userSessionJPARepository.save(session);
    }

    @Override
    public Optional<UserSession> findById(UUID id) {
        return userSessionJPARepository.findById(id);
    }

    @Override
    public List<UserSession> findAllByUserId(UUID userId) {
        return userSessionJPARepository.findAllByUserId(userId);
    }

    @Override
    public void markNotCurrentByUserId(UUID userId) {
        userSessionJPARepository.markNotCurrentByUserId(userId);
    }

    @Override
    public void revokeAllByUserId(UUID userId) {
        for (UserSession session : userSessionJPARepository.findAllByUserId(userId)) {
            session.softDelete();
            userSessionJPARepository.save(session);
        }
    }
}
