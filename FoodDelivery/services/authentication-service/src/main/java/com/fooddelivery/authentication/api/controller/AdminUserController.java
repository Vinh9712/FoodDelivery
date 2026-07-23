package com.fooddelivery.authentication.api.controller;

import com.fooddelivery.commonweb.response.ApiResponse;
import com.fooddelivery.authentication.api.dto.request.CreateAdminUserRequest;
import com.fooddelivery.authentication.api.dto.request.UpdateRoleRequest;
import com.fooddelivery.authentication.api.dto.response.AdminUserPageResponse;
import com.fooddelivery.authentication.api.dto.response.AdminUserResponse;
import com.fooddelivery.authentication.api.dto.response.DashboardStatsResponse;
import com.fooddelivery.authentication.api.dto.response.UserDetailResponse;
import com.fooddelivery.authentication.application.command.*;
import com.fooddelivery.authentication.application.usecase.AdminUserUseCase;
import com.fooddelivery.authentication.config.UserPrincipal;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({ "/api/v1/admin/users", "/admin/users" })
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserUseCase adminUserUseCase;

    public AdminUserController(AdminUserUseCase adminUserUseCase) {
        this.adminUserUseCase = adminUserUseCase;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserPageResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) Boolean active) {
        ListUsersQuery query = new ListUsersQuery(page, size, search, role, active);
        List<AdminUserResponse> items = adminUserUseCase.listUsers(query);
        long total = adminUserUseCase.countUsers(query);
        return ResponseEntity.ok(ApiResponse.ok(new AdminUserPageResponse(items, total)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(@PathVariable UUID id) {
        GetUserDetailQuery query = new GetUserDetailQuery(id);
        UserDetailResponse response = adminUserUseCase.getUserDetail(query);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
            @Valid @RequestBody CreateAdminUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        CreateAdminUserCommand command = new CreateAdminUserCommand(
                principal.userId(), request.email(), request.phone(), request.password(), request.role());
        AdminUserResponse response = adminUserUseCase.createUser(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "User created"));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<ApiResponse<Void>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ChangeUserRoleCommand command = new ChangeUserRoleCommand(id, request.role(), principal.userId());
        adminUserUseCase.changeRole(command);
        return ResponseEntity.ok(ApiResponse.ok(null, "Role updated"));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ToggleUserActiveCommand command = new ToggleUserActiveCommand(id, false, principal.userId());
        adminUserUseCase.toggleActive(command);
        return ResponseEntity.ok(ApiResponse.ok(null, "User deactivated"));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        ToggleUserActiveCommand command = new ToggleUserActiveCommand(id, true, principal.userId());
        adminUserUseCase.toggleActive(command);
        return ResponseEntity.ok(ApiResponse.ok(null, "User activated"));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> getStats() {
        DashboardStatsResponse stats = adminUserUseCase.getStats();
        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
