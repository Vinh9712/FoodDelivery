package com.fooddelivery.delivery.application.service;

import com.fooddelivery.delivery.api.dto.UpdateDriverProfileRequest;
import com.fooddelivery.delivery.domain.exception.DriverNotFoundException;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.domain.model.valueobject.VehicleType;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverSelfServiceTest {

    @Mock
    private DriverRepository driverRepository;
    @Mock
    private DeliveryRepository deliveryRepository;

    private DriverSelfService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new DriverSelfService(driverRepository, deliveryRepository);
        userId = UUID.randomUUID();
    }

    @Test
    void upsertCreatesProfileWhenMissing() {
        when(driverRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        Driver driver = service.upsertProfile(userId, new UpdateDriverProfileRequest(
                "Nguyen Van Shipper", "0901234567", VehicleType.MOTORBIKE, "59A1-12345"));

        assertThat(driver.getUserId()).isEqualTo(userId);
        assertThat(driver.getFullName()).isEqualTo("Nguyen Van Shipper");
        assertThat(driver.getVehicleType()).isEqualTo(VehicleType.MOTORBIKE);
        assertThat(driver.isOnline()).isFalse();
        verify(driverRepository).save(any(Driver.class));
    }

    @Test
    void upsertUpdatesExistingProfile() {
        Driver existing = Driver.createForUser(
                userId, "Old Name", "0900000000", VehicleType.BICYCLE, "59X1-00000");
        when(driverRepository.findByUserId(userId)).thenReturn(Optional.of(existing));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        Driver updated = service.upsertProfile(userId, new UpdateDriverProfileRequest(
                "New Name", "0911111111", VehicleType.CAR, "51G-99999"));

        assertThat(updated.getFullName()).isEqualTo("New Name");
        assertThat(updated.getPhone()).isEqualTo("0911111111");
        assertThat(updated.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(updated.getLicensePlate()).isEqualTo("51G-99999");
    }

    @Test
    void getProfileThrowsWhenMissing() {
        when(driverRepository.findByUserId(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProfile(userId))
                .isInstanceOf(DriverNotFoundException.class);
    }

    @Test
    void listMyDeliveriesFiltersByStatus() {
        Driver driver = Driver.createForUser(
                userId, "Shipper", "0901234567", VehicleType.MOTORBIKE, "59A1-1");
        Delivery delivery = mock(Delivery.class);
        when(driverRepository.findByUserId(userId)).thenReturn(Optional.of(driver));
        when(deliveryRepository.findByDriverIdAndStatusOrderByCreatedAtDesc(
                eq(driver.getId()), eq(DeliveryStatus.DRIVER_ASSIGNED), any()))
                .thenReturn(new PageImpl<>(List.of(delivery)));

        Page<Delivery> page = service.listMyDeliveries(
                userId, DeliveryStatus.DRIVER_ASSIGNED, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void currentDeliveryReturnsActiveJob() {
        Driver driver = Driver.createForUser(
                userId, "Shipper", "0901234567", VehicleType.MOTORBIKE, "59A1-1");
        Delivery delivery = mock(Delivery.class);
        when(driverRepository.findByUserId(userId)).thenReturn(Optional.of(driver));
        when(deliveryRepository.findFirstByDriverIdAndStatusInOrderByUpdatedAtDesc(
                eq(driver.getId()), any()))
                .thenReturn(Optional.of(delivery));

        assertThat(service.currentDelivery(userId)).contains(delivery);
    }

    @Test
    void goOnlineMarksAvailableWhenNoActiveJobs() {
        Driver driver = Driver.createForUser(
                userId, "Shipper", "0901234567", VehicleType.MOTORBIKE, "59A1-1");
        when(driverRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(driver));
        when(deliveryRepository.countActiveByDriver(eq(driver.getId()), any(), eq(null))).thenReturn(0L);
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        Driver online = service.goOnline(userId);

        assertThat(online.isOnline()).isTrue();
        assertThat(online.isAvailable()).isTrue();
        ArgumentCaptor<Driver> captor = ArgumentCaptor.forClass(Driver.class);
        verify(driverRepository).save(captor.capture());
        assertThat(captor.getValue().isOnline()).isTrue();
    }
}
