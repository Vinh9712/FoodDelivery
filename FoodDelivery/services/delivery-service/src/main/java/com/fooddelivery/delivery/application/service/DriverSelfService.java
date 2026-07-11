package com.fooddelivery.delivery.application.service;

import com.fooddelivery.delivery.domain.exception.DriverNotFoundException;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverSelfService {

    private static final List<DeliveryStatus> ACTIVE = List.of(
            DeliveryStatus.DRIVER_ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.DELIVERING);

    private final DriverRepository driverRepository;
    private final DeliveryRepository deliveryRepository;

    @Transactional
    public Driver goOnline(UUID userId) {
        Driver driver = requireDriver(userId);
        driver.goOnline();
        long active = deliveryRepository.countActiveByDriver(driver.getId(), ACTIVE, null);
        if (active == 0) {
            driver.markAvailable();
        }
        return driverRepository.save(driver);
    }

    @Transactional
    public Driver goOffline(UUID userId) {
        Driver driver = requireDriver(userId);
        driver.goOffline();
        return driverRepository.save(driver);
    }

    @Transactional
    public Driver updateLocation(UUID userId, BigDecimal latitude, BigDecimal longitude) {
        Driver driver = requireDriver(userId);
        driver.updateLocation(latitude, longitude);
        return driverRepository.save(driver);
    }

    private Driver requireDriver(UUID userId) {
        return driverRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> DriverNotFoundException.forUser(userId));
    }
}
