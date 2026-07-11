package com.fooddelivery.authentication.application.usecase.impl;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.authentication.api.dto.response.AdminUserResponse;
import com.fooddelivery.authentication.api.dto.response.DashboardStatsResponse;
import com.fooddelivery.authentication.api.dto.response.UserDetailResponse;
import com.fooddelivery.authentication.application.command.ChangeUserRoleCommand;
import com.fooddelivery.authentication.application.command.CreateAdminUserCommand;
import com.fooddelivery.authentication.application.command.GetUserDetailQuery;
import com.fooddelivery.authentication.application.command.ListUsersQuery;
import com.fooddelivery.authentication.application.command.ToggleUserActiveCommand;
import com.fooddelivery.authentication.application.service.SecurityAuditLogger;
import com.fooddelivery.authentication.application.usecase.AdminUserUseCase;
import com.fooddelivery.authentication.domain.model.User;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import com.fooddelivery.authentication.domain.repository.UserRepository;
import com.fooddelivery.authentication.domain.repository.RefreshTokenRepository;
import com.fooddelivery.authentication.domain.repository.UserSessionRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserUseCaseImpl implements AdminUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditLogger auditLogger;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserSessionRepository userSessionRepository;

    public AdminUserUseCaseImpl(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                SecurityAuditLogger auditLogger,
                                RefreshTokenRepository refreshTokenRepository,
                                UserSessionRepository userSessionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userSessionRepository = userSessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(ListUsersQuery query) {
        return userRepository.findAll(
                query.page(),
                query.size(),
                query.search(),
                query.role(),
                query.active()
        ).stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countUsers(ListUsersQuery query) {
        return userRepository.count(query.search(), query.role(), query.active());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(GetUserDetailQuery query) {
        User user = userRepository.findById(query.userId())
                .orElseThrow(() -> new BusinessRuleException("User not found"));

        return UserDetailResponse.from(user);
    }

    @Override
    @Transactional
    public AdminUserResponse createUser(CreateAdminUserCommand command) {
        if (userRepository.existsByEmail(command.email().trim().toLowerCase())) {
            throw new BusinessRuleException("Email already exists");
        }

        if (userRepository.existsByPhone(command.phone().trim())) {
            throw new BusinessRuleException("Phone number already exists");
        }

        String passwordHash = passwordEncoder.encode(command.password());
        User user = User.register(command.email(), command.phone(), passwordHash, command.role());
        user = userRepository.save(user);
        auditLogger.record("ADMIN_CREATE_USER", "SUCCESS", command.currentUserId(), user.getId(), user.getEmail(), null);

        return AdminUserResponse.from(user);
    }

    @Override
    @Transactional
    public void changeRole(ChangeUserRoleCommand command) {
        if (command.newRole() == null) {
            throw new BusinessRuleException("Role is required");
        }

        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessRuleException("User not found"));

        if (user.getId().equals(command.currentUserId()) && command.newRole() != user.getRole()) {
            throw new BusinessRuleException("Cannot change your own role");
        }

        user.changeRole(command.newRole());
        userRepository.save(user);
        revokeUserSessions(user);
        auditLogger.record("ADMIN_CHANGE_ROLE", "SUCCESS", command.currentUserId(), user.getId(), user.getEmail(), null);
    }

    @Override
    @Transactional
    public void toggleActive(ToggleUserActiveCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(() -> new BusinessRuleException("User not found"));

        if (user.getId().equals(command.currentUserId())) {
            throw new BusinessRuleException("Cannot deactivate your own account");
        }

        if (command.activate()) {
            user.activate();
        } else {
            user.deactivate();
            revokeUserSessions(user);
        }
        userRepository.save(user);
        auditLogger.record(command.activate() ? "ADMIN_ACTIVATE_USER" : "ADMIN_DEACTIVATE_USER",
                "SUCCESS",
                command.currentUserId(),
                user.getId(),
                user.getEmail(),
                null);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        long totalUsers = userRepository.count(null, null, null);
        long activeUsers = userRepository.count(null, null, true);

        Map<String, Long> usersByRole = new HashMap<>();
        for (UserRole role : UserRole.values()) {
            long count = userRepository.countByRole(role);
            if (count > 0) {
                usersByRole.put(role.name(), count);
            }
        }

        return new DashboardStatsResponse(totalUsers, activeUsers, usersByRole);
    }

    private void revokeUserSessions(User user) {
        refreshTokenRepository.revokeAllByUserId(user.getId());
        userSessionRepository.findAllByUserId(user.getId()).forEach(session -> {
            session.softDelete();
            userSessionRepository.save(session);
        });
    }
}
