package com.fooddelivery.customer.application.usecase;

import com.fooddelivery.customer.application.command.AddAddressCommand;
import com.fooddelivery.customer.application.command.RemoveAddressCommand;
import com.fooddelivery.customer.application.command.SetDefaultAddressCommand;
import com.fooddelivery.customer.application.command.UpdateAddressCommand;
import com.fooddelivery.customer.api.dto.response.AddressResponse;

import java.util.List;
import java.util.UUID;

public interface ManageAddressUseCase {
    List<AddressResponse> getAddresses(UUID authUserId);
    AddressResponse addAddress(AddAddressCommand command);
    AddressResponse updateAddress(UpdateAddressCommand command);
    AddressResponse setDefaultAddress(SetDefaultAddressCommand command);
    void removeAddress(RemoveAddressCommand command);
}
