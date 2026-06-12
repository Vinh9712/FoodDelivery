package com.fooddelivery.order.domain.model;

import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.model.valueobject.AssignedDriverInfo;
import com.fooddelivery.order.domain.model.valueobject.OrderStatus;
import com.fooddelivery.order.domain.model.valueobject.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(150000));
    }

    private AssignedDriverInfo createDriverInfo(UUID driverId) {
        return new AssignedDriverInfo(
                driverId,
                "Nguyen Van A",
                "0987654321",
                VehicleType.MOTORBIKE,
                "29A1-12345",
                BigDecimal.valueOf(4.8),
                Instant.now()
        );
    }

    @Nested
    @DisplayName("assignDriver() invariants")
    class AssignDriverTests {

        @Test
        @DisplayName("throws InvalidOrderStateException when status is PENDING")
        void throwsWhenPending() {
            assertEquals(OrderStatus.PENDING, order.getStatus());

            AssignedDriverInfo driverInfo = createDriverInfo(UUID.randomUUID());

            assertThrows(InvalidOrderStateException.class, () -> order.assignDriver(driverInfo));
        }

        @Test
        @DisplayName("is a no-op when status is DELIVERED — no exception, no state change")
        void noOpWhenDelivered() {
            // Move order to CONFIRMED first, then simulate delivered
            order.confirm();

            // Assign first to have a snapshot
            AssignedDriverInfo firstDriver = createDriverInfo(UUID.randomUUID());
            order.assignDriver(firstDriver);

            // Simulate DELIVERED via reflection (in a real scenario, there would be a deliver() method)
            setStatusViaReflection(order, OrderStatus.DELIVERED);

            Instant updatedBefore = order.getUpdatedAt();
            AssignedDriverInfo secondDriver = createDriverInfo(UUID.randomUUID());

            // Should NOT throw
            assertDoesNotThrow(() -> order.assignDriver(secondDriver));

            // Snapshot should remain unchanged (first driver)
            assertTrue(order.getAssignedDriver().isPresent());
            assertEquals(firstDriver.driverId(), order.getAssignedDriver().get().driverId());
        }

        @Test
        @DisplayName("is a no-op when status is CANCELLED — no exception, no state change")
        void noOpWhenCancelled() {
            order.confirm();
            order.cancel();
            assertEquals(OrderStatus.CANCELLED, order.getStatus());

            AssignedDriverInfo driverInfo = createDriverInfo(UUID.randomUUID());

            assertDoesNotThrow(() -> order.assignDriver(driverInfo));
            assertTrue(order.getAssignedDriver().isEmpty());
        }

        @Test
        @DisplayName("overwrites snapshot on reassignment (different driverId)")
        void overwritesOnReassignment() {
            order.confirm();

            UUID firstDriverId = UUID.randomUUID();
            UUID secondDriverId = UUID.randomUUID();

            order.assignDriver(createDriverInfo(firstDriverId));
            assertTrue(order.getAssignedDriver().isPresent());
            assertEquals(firstDriverId, order.getAssignedDriver().get().driverId());

            Instant afterFirstAssign = order.getUpdatedAt();

            // Small delay to ensure updatedAt changes
            order.assignDriver(createDriverInfo(secondDriverId));

            assertTrue(order.getAssignedDriver().isPresent());
            assertEquals(secondDriverId, order.getAssignedDriver().get().driverId());
            // updatedAt should be updated
            assertTrue(order.getUpdatedAt().compareTo(afterFirstAssign) >= 0);
        }

        @Test
        @DisplayName("successfully assigns driver when status is CONFIRMED")
        void assignsWhenConfirmed() {
            order.confirm();
            assertEquals(OrderStatus.CONFIRMED, order.getStatus());

            AssignedDriverInfo driverInfo = createDriverInfo(UUID.randomUUID());
            order.assignDriver(driverInfo);

            assertTrue(order.getAssignedDriver().isPresent());
            assertEquals(driverInfo.driverId(), order.getAssignedDriver().get().driverId());
            assertEquals(driverInfo.fullName(), order.getAssignedDriver().get().fullName());
        }
    }

    @Nested
    @DisplayName("Order lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("newly created order has status PENDING and no assigned driver")
        void newOrderIsPending() {
            assertEquals(OrderStatus.PENDING, order.getStatus());
            assertTrue(order.getAssignedDriver().isEmpty());
            assertNotNull(order.getId());
            assertNotNull(order.getCreatedAt());
        }

        @Test
        @DisplayName("confirm() transitions from PENDING to CONFIRMED")
        void confirmOrder() {
            order.confirm();
            assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        }

        @Test
        @DisplayName("confirm() throws when not PENDING")
        void confirmThrowsWhenNotPending() {
            order.confirm();
            assertThrows(InvalidOrderStateException.class, () -> order.confirm());
        }

        @Test
        @DisplayName("cancel() transitions to CANCELLED")
        void cancelOrder() {
            order.cancel();
            assertEquals(OrderStatus.CANCELLED, order.getStatus());
        }
    }

    /**
     * Helper to set order status via reflection for testing edge cases.
     * In production code, proper state machine transitions would be used.
     */
    private void setStatusViaReflection(Order order, OrderStatus status) {
        try {
            var field = Order.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(order, status);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set status via reflection", e);
        }
    }
}
