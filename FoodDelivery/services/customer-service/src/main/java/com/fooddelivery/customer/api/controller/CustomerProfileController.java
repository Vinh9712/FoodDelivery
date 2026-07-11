package com.fooddelivery.customer.api.controller;

import com.fooddelivery.commonweb.response.ApiResponse;
import com.fooddelivery.customer.api.dto.request.AddAddressRequest;
import com.fooddelivery.customer.api.dto.request.UpdateProfileRequest;
import com.fooddelivery.customer.api.dto.response.AddressResponse;
import com.fooddelivery.customer.api.dto.response.CustomerProfileResponse;
import com.fooddelivery.customer.application.command.AddAddressCommand;
import com.fooddelivery.customer.application.command.RemoveAddressCommand;
import com.fooddelivery.customer.application.command.SetDefaultAddressCommand;
import com.fooddelivery.customer.application.command.UpdateAddressCommand;
import com.fooddelivery.customer.application.command.UpdateProfileCommand;
import com.fooddelivery.customer.application.usecase.ManageAddressUseCase;
import com.fooddelivery.customer.application.usecase.UpdateProfileUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/customers", "/customers"})
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerProfileController {

    private final UpdateProfileUseCase updateProfileUseCase;
    private final ManageAddressUseCase manageAddressUseCase;

    public CustomerProfileController(
            UpdateProfileUseCase updateProfileUseCase,
            ManageAddressUseCase manageAddressUseCase) {
        this.updateProfileUseCase = updateProfileUseCase;
        this.manageAddressUseCase = manageAddressUseCase;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getMyProfile(
            @AuthenticationPrincipal Jwt jwt) {
        UUID authUserId = authenticatedUserId(jwt);
        CustomerProfileResponse response = updateProfileUseCase.getProfile(authUserId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID authUserId = authenticatedUserId(jwt);
        UpdateProfileCommand command = new UpdateProfileCommand(
                authUserId,
                request.fullName(),
                request.phone(),
                request.avatarUrl()
        );
        CustomerProfileResponse response = updateProfileUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "Profile updated successfully"));
    }

    @GetMapping("/me/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses(
            @AuthenticationPrincipal Jwt jwt) {
        UUID authUserId = authenticatedUserId(jwt);
        List<AddressResponse> response = manageAddressUseCase.getAddresses(authUserId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addMyAddress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddAddressRequest request) {
        UUID authUserId = authenticatedUserId(jwt);
        AddAddressCommand command = new AddAddressCommand(
                authUserId,
                request.label(),
                request.addressLine(),
                request.district(),
                request.city(),
                request.latitude(),
                request.longitude(),
                request.defaultAddress()
        );
        AddressResponse response = manageAddressUseCase.addAddress(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "Address added successfully"));
    }

    @PutMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateMyAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddAddressRequest request) {
        UUID authUserId = authenticatedUserId(jwt);
        UpdateAddressCommand command = new UpdateAddressCommand(
                authUserId,
                addressId,
                request.label(),
                request.addressLine(),
                request.district(),
                request.city(),
                request.latitude(),
                request.longitude(),
                request.defaultAddress()
        );
        AddressResponse response = manageAddressUseCase.updateAddress(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "Address updated successfully"));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteMyAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        UUID authUserId = authenticatedUserId(jwt);
        RemoveAddressCommand command = new RemoveAddressCommand(authUserId, addressId);
        manageAddressUseCase.removeAddress(command);
        return ResponseEntity.ok(ApiResponse.ok(null, "Address deleted successfully"));
    }

    @PatchMapping("/me/addresses/{addressId}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId) {
        UUID authUserId = authenticatedUserId(jwt);
        AddressResponse response = manageAddressUseCase.setDefaultAddress(new SetDefaultAddressCommand(authUserId, addressId));
        return ResponseEntity.ok(ApiResponse.ok(response, "Default address updated successfully"));
    }

    private UUID authenticatedUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
