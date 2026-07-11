package com.fooddelivery.authentication.application.usecase.impl;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.authentication.api.dto.response.SessionResponse;
import com.fooddelivery.authentication.application.command.GetSessionsQuery;
import com.fooddelivery.authentication.application.command.RevokeOthersCommand;
import com.fooddelivery.authentication.application.command.RevokeSessionCommand;
import com.fooddelivery.authentication.application.usecase.SessionUseCase;
import com.fooddelivery.authentication.domain.model.UserSession;
import com.fooddelivery.authentication.domain.repository.RefreshTokenRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SessionUseCaseImpl implements SessionUseCase {

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public SessionUseCaseImpl(UserSessionRepository userSessionRepository,
                              RefreshTokenRepository refreshTokenRepository) {
        this.userSessionRepository = userSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> getSessions(GetSessionsQuery query) {
        return userSessionRepository.findAllByUserId(query.userId())
                .stream()
                .map(SessionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public void revokeSession(RevokeSessionCommand command) {
        UserSession session = userSessionRepository.findById(command.sessionId())
                .orElseThrow(() -> new BusinessRuleException("Session not found"));

        if (!session.getUser().getId().equals(command.userId())) {
            throw new BusinessRuleException("Session does not belong to this user");
        }

        refreshTokenRepository.revokeAllBySessionId(session.getId());
        session.softDelete();
        userSessionRepository.save(session);
    }

    @Override
    @Transactional
    public void revokeOthers(RevokeOthersCommand command) {
        List<UserSession> sessions = userSessionRepository.findAllByUserId(command.userId());
        for (UserSession session : sessions) {
            if (!session.getId().equals(command.currentSessionId())) {
                refreshTokenRepository.revokeAllBySessionId(session.getId());
                session.softDelete();
                userSessionRepository.save(session);
            }
        }
    }
}
