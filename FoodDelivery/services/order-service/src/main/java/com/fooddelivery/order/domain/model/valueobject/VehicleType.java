package com.fooddelivery.order.domain.model.valueobject;

/**
 * Vehicle type enum — local copy within Order Service bounded context.
 * Mirrors Delivery Service's VehicleType without sharing classes.
 */
public enum VehicleType {
    MOTORBIKE,
    CAR,
    BICYCLE
}
