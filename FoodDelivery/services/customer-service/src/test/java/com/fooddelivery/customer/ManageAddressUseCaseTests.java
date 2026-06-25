package com.fooddelivery.customer;

import com.fooddelivery.customer.application.command.AddAddressCommand;
import com.fooddelivery.customer.application.command.RemoveAddressCommand;
import com.fooddelivery.customer.api.dto.response.AddressResponse;
import com.fooddelivery.customer.application.usecase.impl.ManageAddressUseCaseImpl;
import com.fooddelivery.customer.domain.model.Address;
import com.fooddelivery.customer.domain.model.Customer;
import com.fooddelivery.customer.domain.model.User;
import com.fooddelivery.customer.domain.model.enums.UserRole;
import com.fooddelivery.customer.domain.repository.CustomerRepository;
import com.github.f4b6a3.uuid.UuidCreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ManageAddressUseCaseTests {

    private CustomerRepository customerRepository;
    private ManageAddressUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        useCase = new ManageAddressUseCaseImpl(customerRepository);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void addAddress_ShouldAllowOnlyOneDefaultAddress() {
        User user = User.register("test@gmail.com", "0987654321", "hashed", UserRole.CUSTOMER);
        Customer customer = Customer.create(user, "Nguyen Van A", "0987654321");
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        AddAddressCommand cmd1 = new AddAddressCommand(
                userId,
                "Home",
                "123 Street",
                null,
                "HCM",
                null,
                null,
                true
        );

        when(customerRepository.findByUserId(any())).thenReturn(Optional.of(customer));

        AddressResponse res1 = useCase.addAddress(cmd1);
        assertTrue(res1.defaultAddress());

        AddAddressCommand cmd2 = new AddAddressCommand(
                userId,
                "Work",
                "456 Blvd",
                null,
                "HCM",
                null,
                null,
                true
        );

        AddressResponse res2 = useCase.addAddress(cmd2);
        assertTrue(res2.defaultAddress());

        Address firstAddress = customer.getAddresses().get(0);
        assertFalse(firstAddress.isDefaultAddress());
    }

    @Test
    void removeAddress_ShouldSoftDeleteDefaultAddress() throws Exception {
        User user = User.register("test@gmail.com", "0987654321", "hashed", UserRole.CUSTOMER);
        Customer customer = Customer.create(user, "Nguyen Van A", "0987654321");

        Address addr = customer.addAddress("Home", "123 Street", "Dist 1", "HCM", BigDecimal.ZERO, BigDecimal.ZERO,
                true);
        UUID addressId = UuidCreator.getTimeOrderedEpoch();
        setPrivateField(addr, "id", addressId);
        UUID userId = UuidCreator.getTimeOrderedEpoch();

        when(customerRepository.findByUserId(any())).thenReturn(Optional.of(customer));

        RemoveAddressCommand cmd = new RemoveAddressCommand(userId, addressId);
        useCase.removeAddress(cmd);

        assertTrue(addr.isDeleted());
        assertNotNull(addr.getDeletedAt());
        assertFalse(addr.isDefaultAddress());
    }
}