package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.api.dto.response.AdminUserResponse;
import com.fooddelivery.customer.api.dto.response.DashboardStatsResponse;
import com.fooddelivery.customer.api.dto.response.UserDetailResponse;
import com.fooddelivery.customer.application.command.ChangeUserRoleCommand;
import com.fooddelivery.customer.application.command.CreateAdminUserCommand;
import com.fooddelivery.customer.application.command.GetUserDetailQuery;
import com.fooddelivery.customer.application.command.ListUsersQuery;
import com.fooddelivery.customer.application.command.ToggleUserActiveCommand;

import java.util.List;

public interface AdminUserUseCase {
    List<AdminUserResponse> listUsers(ListUsersQuery query);

    long countUsers(ListUsersQuery query);

    UserDetailResponse getUserDetail(GetUserDetailQuery query);

    AdminUserResponse createUser(CreateAdminUserCommand command);

    void changeRole(ChangeUserRoleCommand command);

    void toggleActive(ToggleUserActiveCommand command);

    DashboardStatsResponse getStats();
}
