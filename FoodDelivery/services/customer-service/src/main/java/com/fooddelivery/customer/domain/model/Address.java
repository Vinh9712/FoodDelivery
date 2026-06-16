package com.fooddelivery.customer.domain.model;

import com.fooddelivery.customer.domain.model.valueobject.AddressLabel;
import com.fooddelivery.customer.domain.model.valueobject.GeoLocation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.fooddelivery.customer.domain.util.UuidCreator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(name = "district", nullable = false)
    private String district;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Address(UUID id, UUID customerId, AddressLabel label, String addressLine, String district, String city, GeoLocation location) {
        this.id = id;
        this.customerId = customerId;
        this.label = label.value();
        this.addressLine = addressLine;
        this.district = district;
        this.city = city;
        if (location != null) {
            this.latitude = location.latitude();
            this.longitude = location.longitude();
        }
        this.isDefault = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Address create(UUID customerId, AddressLabel label, String addressLine, String district, String city, GeoLocation location) {
        return new Address(UuidCreator.nextUuidV7(), customerId, label, addressLine, district, city, location);
    }

    public AddressLabel getLabel() {
        return new AddressLabel(this.label);
    }

    public GeoLocation getLocation() {
        if (this.latitude == null || this.longitude == null) {
            return null;
        }
        return new GeoLocation(this.latitude, this.longitude);
    }

    public void markAsDefault() {
        this.isDefault = true;
        this.updatedAt = Instant.now();
    }

    public void unmarkAsDefault() {
        this.isDefault = false;
        this.updatedAt = Instant.now();
    }
}
