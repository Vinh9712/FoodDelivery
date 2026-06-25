package com.fooddelivery.customer.application.usecase.impl;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.customer.api.dto.response.AdminUserResponse;
import com.fooddelivery.customer.api.dto.response.DashboardStatsResponse;
import com.fooddelivery.customer.api.dto.response.UserDetailResponse;
import com.fooddelivery.customer.application.command.ChangeUserRoleCommand;
import com.fooddelivery.customer.application.command.CreateAdminUserCommand;
import com.fooddelivery.customer.application.command.GetUserDetailQuery;
import com.fooddelivery.customer.application.command.ListUsersQuery;
import com.fooddelivery.customer.application.command.ToggleUserActiveCommand;
import com.fooddelivery.customer.application.usecase.AdminUserUseCase;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import com.fooddelivery.customer.domain.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminUserUseCaseImpl implements AdminUserUseCase {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserUseCaseImpl(UserRepository userRepository,
                                CustomerRepository customerRepository,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
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

        Optional<Customer> customer = customerRepository.findByUserId(user.getId());

        return UserDetailResponse.from(user, customer.orElse(null));
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
        }
        userRepository.save(user);
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
}
