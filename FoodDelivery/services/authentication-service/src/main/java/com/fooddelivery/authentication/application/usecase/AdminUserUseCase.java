package com.fooddelivery.authentication.application.usecase;

import com.fooddelivery.authentication.api.dto.response.AdminUserResponse;
import com.fooddelivery.authentication.api.dto.response.DashboardStatsResponse;
import com.fooddelivery.authentication.api.dto.response.UserDetailResponse;
import com.fooddelivery.authentication.application.command.ChangeUserRoleCommand;
import com.fooddelivery.authentication.application.command.CreateAdminUserCommand;
import com.fooddelivery.authentication.application.command.GetUserDetailQuery;
import com.fooddelivery.authentication.application.command.ListUsersQuery;
import com.fooddelivery.authentication.application.command.ToggleUserActiveCommand;

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
