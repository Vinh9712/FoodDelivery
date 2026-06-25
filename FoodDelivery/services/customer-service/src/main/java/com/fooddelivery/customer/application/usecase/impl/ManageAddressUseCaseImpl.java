package com.fooddelivery.customer.application.usecase.impl;

import com.fooddelivery.commonweb.exception.NotFoundException;
import com.fooddelivery.customer.application.command.AddAddressCommand;
import com.fooddelivery.customer.application.command.RemoveAddressCommand;
import com.fooddelivery.customer.application.command.UpdateAddressCommand;
import com.fooddelivery.customer.api.dto.response.AddressResponse;
import com.fooddelivery.customer.application.usecase.ManageAddressUseCase;
import com.fooddelivery.customer.domain.model.Address;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ManageAddressUseCaseImpl implements ManageAddressUseCase {

    private final CustomerRepository customerRepository;

    public ManageAddressUseCaseImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(UUID userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));

        return customer.getAddresses().stream()
                .filter(address -> !address.isDeleted())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AddressResponse addAddress(AddAddressCommand command) {
        Customer customer = customerRepository.findByUserId(command.userId())
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));

        boolean isFirstAddress = customer.getAddresses().stream()
                .noneMatch(address -> !address.isDeleted());

        boolean makeDefault = command.defaultAddress() || isFirstAddress;

        Address address = customer.addAddress(
                command.label(),
                command.addressLine(),
                command.district(),
                command.city(),
                command.latitude(),
                command.longitude(),
                makeDefault
        );

        customerRepository.save(customer);

        return mapToResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(UpdateAddressCommand command) {
        Customer customer = customerRepository.findByUserId(command.userId())
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));

        Address address = customer.getAddresses().stream()
                .filter(a -> a.getId().equals(command.addressId()) && !a.isDeleted())
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Address not found with id: " + command.addressId()));

        address.update(
                command.label(),
                command.addressLine(),
                command.district(),
                command.city(),
                command.latitude(),
                command.longitude()
        );

        if (command.defaultAddress()) {
            customer.setDefaultAddress(command.addressId());
        } else {
            address.unsetDefault();
        }

        customerRepository.save(customer);

        return mapToResponse(address);
    }

    @Override
    @Transactional
    public void removeAddress(RemoveAddressCommand command) {
        Customer customer = customerRepository.findByUserId(command.userId())
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));

        boolean exists = customer.getAddresses().stream()
                .anyMatch(a -> a.getId().equals(command.addressId()) && !a.isDeleted());
        if (!exists) {
            throw new NotFoundException("Address not found with id: " + command.addressId());
        }

        customer.removeAddress(command.addressId());
        customerRepository.save(customer);
    }

    private AddressResponse mapToResponse(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getAddressLine(),
                address.getDistrict(),
                address.getCity(),
                address.getLatitude(),
                address.getLongitude(),
                address.isDefaultAddress()
        );
    }
}