package com.fooddelivery.customer.domain.model;

import com.fooddelivery.commonweb.base.BaseEntity;
import com.fooddelivery.customer.domain.model.valueobject.GeoLocation;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "addresses")
@SQLRestriction("is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "address_line", nullable = false, columnDefinition = "TEXT")
    private String addressLine;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
    public static Address create(Customer customer, String label, String addressLine, String district,
                                 String city, BigDecimal latitude, BigDecimal longitude, boolean defaultAddress) {
        return create(customer, label, addressLine, district, city, GeoLocation.ofNullable(latitude, longitude), defaultAddress);
    }

    public static Address create(Customer customer, String label, String addressLine, String district,
                                 String city, GeoLocation location, boolean defaultAddress) {
        if (customer == null) {
            throw new IllegalArgumentException("customer is required");
        }
        if (isBlank(addressLine) || isBlank(city)) {
            throw new IllegalArgumentException("addressLine and city are required");
        }
        Address address = new Address();
        address.id = UuidCreator.getTimeOrderedEpoch();
        address.customer = customer;
        address.label = label;
        address.addressLine = addressLine.trim();
        address.district = district;
        address.city = city.trim();
        address.latitude = location != null ? location.latitude() : null;
        address.longitude = location != null ? location.longitude() : null;
        address.defaultAddress = defaultAddress;
        return address;
    }

    public void update(String label, String addressLine, String district,
                       String city, BigDecimal latitude, BigDecimal longitude) {
        update(label, addressLine, district, city, GeoLocation.ofNullable(latitude, longitude));
    }

    public void update(String label, String addressLine, String district,
                       String city, GeoLocation location) {
        if (isBlank(addressLine) || isBlank(city)) {
            throw new IllegalArgumentException("addressLine and city are required");
        }
        this.label = label;
        this.addressLine = addressLine.trim();
        this.district = district;
        this.city = city.trim();
        this.latitude = location != null ? location.latitude() : null;
        this.longitude = location != null ? location.longitude() : null;
    }

    public void setDefault() {
        this.defaultAddress = true;
    }

    public void unsetDefault() {
        this.defaultAddress = false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
