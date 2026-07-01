package com.fooddelivery.customer;

import com.fooddelivery.customer.application.command.AddAddressCommand;
import com.fooddelivery.customer.application.command.RemoveAddressCommand;
import com.fooddelivery.customer.application.command.SetDefaultAddressCommand;
import com.fooddelivery.customer.api.dto.response.AddressResponse;
import com.fooddelivery.customer.application.usecase.impl.ManageAddressUseCaseImpl;
import com.fooddelivery.customer.domain.model.Address;
import com.fooddelivery.customer.domain.model.Customer;
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
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        Customer customer = Customer.create(userId, "Nguyen Van A", "0987654321");

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
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        Customer customer = Customer.create(userId, "Nguyen Van A", "0987654321");

        Address addr = customer.addAddress("Home", "123 Street", "Dist 1", "HCM", BigDecimal.ZERO, BigDecimal.ZERO,
                true);
        UUID addressId = UuidCreator.getTimeOrderedEpoch();
        setPrivateField(addr, "id", addressId);

        when(customerRepository.findByUserId(any())).thenReturn(Optional.of(customer));

        RemoveAddressCommand cmd = new RemoveAddressCommand(userId, addressId);
        useCase.removeAddress(cmd);

        assertTrue(addr.isDeleted());
        assertNotNull(addr.getDeletedAt());
        assertFalse(addr.isDefaultAddress());
    }

    @Test
    void setDefaultAddress_ShouldUnsetPreviousDefault() throws Exception {
        UUID userId = UuidCreator.getTimeOrderedEpoch();
        Customer customer = Customer.create(userId, "Nguyen Van A", "0987654321");
        Address home = customer.addAddress("Home", "123 Street", "Dist 1", "HCM", null, null, true);
        Address work = customer.addAddress("Work", "456 Blvd", "Dist 3", "HCM", null, null, false);
        UUID homeId = UuidCreator.getTimeOrderedEpoch();
        UUID workId = UuidCreator.getTimeOrderedEpoch();
        setPrivateField(home, "id", homeId);
        setPrivateField(work, "id", workId);

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));

        AddressResponse response = useCase.setDefaultAddress(new SetDefaultAddressCommand(userId, workId));

        assertEquals(workId, response.id());
        assertTrue(work.isDefaultAddress());
        assertFalse(home.isDefaultAddress());
        verify(customerRepository).save(customer);
    }
}
