package com.fooddelivery.delivery.application.service;

import com.fooddelivery.delivery.api.dto.UpdateDriverProfileRequest;
import com.fooddelivery.delivery.domain.exception.DriverNotFoundException;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverSelfService {

    private static final List<DeliveryStatus> ACTIVE = List.of(
            DeliveryStatus.DRIVER_ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.DELIVERING);

    private final DriverRepository driverRepository;
    private final DeliveryRepository deliveryRepository;

    @Transactional(readOnly = true)
    public Driver getProfile(UUID userId) {
        return requireDriver(userId);
    }

    /**
     * Create or update the driver profile for the authenticated user.
     */
    @Transactional
    public Driver upsertProfile(UUID userId, UpdateDriverProfileRequest request) {
        Optional<Driver> existing = driverRepository.findByUserId(userId);
        if (existing.isPresent()) {
            Driver driver = existing.get();
            driver.updateProfile(
                    request.fullName(), request.phone(), request.vehicleType(), request.licensePlate());
            return driverRepository.save(driver);
        }
        Driver created = Driver.createForUser(
                userId,
                request.fullName().trim(),
                request.phone().trim(),
                request.vehicleType(),
                request.licensePlate().trim());
        return driverRepository.save(created);
    }

    @Transactional(readOnly = true)
    public Page<Delivery> listMyDeliveries(UUID userId, DeliveryStatus status, Pageable pageable) {
        Driver driver = requireDriver(userId);
        if (status != null) {
            return deliveryRepository.findByDriverIdAndStatusOrderByCreatedAtDesc(
                    driver.getId(), status, pageable);
        }
        return deliveryRepository.findByDriverIdOrderByCreatedAtDesc(driver.getId(), pageable);
    }

    @Transactional(readOnly = true)
    public Optional<Delivery> currentDelivery(UUID userId) {
        Driver driver = requireDriver(userId);
        return deliveryRepository.findFirstByDriverIdAndStatusInOrderByUpdatedAtDesc(
                driver.getId(), ACTIVE);
    }

    @Transactional
    public Driver goOnline(UUID userId) {
        Driver driver = requireDriverForUpdate(userId);
        driver.goOnline();
        long active = deliveryRepository.countActiveByDriver(driver.getId(), ACTIVE, null);
        if (active == 0) {
            driver.markAvailable();
        }
        return driverRepository.save(driver);
    }

    @Transactional
    public Driver goOffline(UUID userId) {
        Driver driver = requireDriverForUpdate(userId);
        driver.goOffline();
        return driverRepository.save(driver);
    }

    @Transactional
    public Driver updateLocation(UUID userId, BigDecimal latitude, BigDecimal longitude) {
        Driver driver = requireDriverForUpdate(userId);
        driver.updateLocation(latitude, longitude);
        return driverRepository.save(driver);
    }

    private Driver requireDriver(UUID userId) {
        return driverRepository.findByUserId(userId)
                .orElseThrow(() -> DriverNotFoundException.forUser(userId));
    }

    private Driver requireDriverForUpdate(UUID userId) {
        return driverRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> DriverNotFoundException.forUser(userId));
    }
}
