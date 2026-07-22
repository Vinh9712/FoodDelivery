package com.fooddelivery.commonevents.delivery;

import java.time.Instant;
import java.util.UUID;

public final class DeliveryEventPayloads {
    public enum FailureCode { NO_DRIVER, DRIVER_REPORTED, SYSTEM_ERROR }

    public record DriverSnapshot(UUID driverId, String fullName, String phone,
                                 String vehicleType, String licensePlate) {
        public DriverSnapshot {
            required(driverId, "driverId"); text(fullName, "fullName"); text(phone, "phone");
            text(vehicleType, "vehicleType"); text(licensePlate, "licensePlate");
        }
    }

    public record DriverAssigned(UUID orderId, UUID deliveryId, UUID customerId,
                                 DriverSnapshot driver, Instant assignedAt) {
        public DriverAssigned {
            required(orderId, "orderId"); required(deliveryId, "deliveryId"); required(customerId, "customerId");
            required(driver, "driver"); required(assignedAt, "assignedAt");
        }
    }

    public record DeliveryPickedUp(UUID orderId, UUID deliveryId, UUID customerId,
                                   UUID driverId, Instant pickedUpAt) {
        public DeliveryPickedUp {
            required(orderId, "orderId"); required(deliveryId, "deliveryId"); required(customerId, "customerId");
            required(driverId, "driverId"); required(pickedUpAt, "pickedUpAt");
        }
    }

    public record DeliveryInTransit(UUID orderId, UUID deliveryId, UUID customerId,
                                    UUID driverId, Instant deliveryStartedAt) {
        public DeliveryInTransit {
            required(orderId, "orderId"); required(deliveryId, "deliveryId"); required(customerId, "customerId");
            required(driverId, "driverId"); required(deliveryStartedAt, "deliveryStartedAt");
        }
    }

    public record DeliveryCompleted(UUID orderId, UUID deliveryId, UUID customerId,
                                    UUID driverId, Instant deliveredAt) {
        public DeliveryCompleted {
            required(orderId, "orderId"); required(deliveryId, "deliveryId"); required(customerId, "customerId");
            required(driverId, "driverId"); required(deliveredAt, "deliveredAt");
        }
    }

    public record DeliveryFailed(UUID orderId, UUID deliveryId, UUID customerId,
                                 UUID driverId, FailureCode failureCode, String reason, Instant failedAt) {
        public DeliveryFailed {
            required(orderId, "orderId"); required(deliveryId, "deliveryId"); required(customerId, "customerId");
            required(failureCode, "failureCode"); text(reason, "reason"); required(failedAt, "failedAt");
        }
    }

    private static void required(Object value, String name) { if (value == null) throw new IllegalArgumentException(name + " is required"); }
    private static void text(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required"); }
    private DeliveryEventPayloads() {}
}
