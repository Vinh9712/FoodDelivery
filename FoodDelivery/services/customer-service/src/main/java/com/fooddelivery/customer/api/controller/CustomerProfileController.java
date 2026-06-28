package com.fooddelivery.customer.api.controller;

import com.fooddelivery.commonweb.response.ApiResponse;
import com.fooddelivery.customer.api.dto.request.AddAddressRequest;
import com.fooddelivery.customer.api.dto.request.UpdateProfileRequest;
import com.fooddelivery.customer.api.dto.response.AddressResponse;
import com.fooddelivery.customer.api.dto.response.CustomerProfileResponse;
import com.fooddelivery.customer.application.command.AddAddressCommand;
import com.fooddelivery.customer.application.command.RemoveAddressCommand;
import com.fooddelivery.customer.application.command.UpdateAddressCommand;
import com.fooddelivery.customer.application.command.UpdateProfileCommand;
import com.fooddelivery.customer.application.usecase.ManageAddressUseCase;
import com.fooddelivery.customer.application.usecase.UpdateProfileUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/customers", "/customers"})
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
            @RequestHeader("X-User-Id") UUID userId) {
        CustomerProfileResponse response = updateProfileUseCase.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateMyProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        UpdateProfileCommand command = new UpdateProfileCommand(
                userId,
                request.fullName(),
                request.phone(),
                request.avatarUrl()
        );
        CustomerProfileResponse response = updateProfileUseCase.execute(command);
        return ResponseEntity.ok(ApiResponse.ok(response, "Profile updated successfully"));
    }

    @GetMapping("/me/addresses")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses(
            @RequestHeader("X-User-Id") UUID userId) {
        List<AddressResponse> response = manageAddressUseCase.getAddresses(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addMyAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AddAddressRequest request) {
        AddAddressCommand command = new AddAddressCommand(
                userId,
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
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddAddressRequest request) {
        UpdateAddressCommand command = new UpdateAddressCommand(
                userId,
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
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId) {
        RemoveAddressCommand command = new RemoveAddressCommand(userId, addressId);
        manageAddressUseCase.removeAddress(command);
        return ResponseEntity.ok(ApiResponse.ok(null, "Address deleted successfully"));
    }
}
