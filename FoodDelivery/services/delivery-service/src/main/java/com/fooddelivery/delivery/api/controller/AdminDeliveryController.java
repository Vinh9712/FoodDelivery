package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.api.dto.DeliveryDetailResponse;
import com.fooddelivery.delivery.api.dto.DriverProfileResponse;
import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.DeliveryStatus;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryController {

    private final DriverRepository driverRepository;
    private final DeliveryRepository deliveryRepository;

    @GetMapping("/drivers")
    public ResponseEntity<Page<DriverProfileResponse>> listDrivers(
            @RequestParam(required = false) Boolean online,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<Driver> page = online == null
                ? driverRepository.findAll(pageable)
                : driverRepository.findByIsOnline(online, pageable);
        return ResponseEntity.ok(page.map(DriverProfileResponse::from));
    }

    @GetMapping("/deliveries")
    public ResponseEntity<Page<DeliveryDetailResponse>> listDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Delivery> page = status == null
                ? deliveryRepository.findAll(pageable)
                : deliveryRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return ResponseEntity.ok(page.map(DeliveryDetailResponse::from));
    }
}
