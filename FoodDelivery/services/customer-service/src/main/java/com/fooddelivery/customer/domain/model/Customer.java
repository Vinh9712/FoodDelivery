package com.fooddelivery.customer.domain.model;

import com.fooddelivery.commonweb.base.BaseEntity;
import com.fooddelivery.customer.domain.exception.AddressNotFoundException;
import com.fooddelivery.customer.domain.model.enums.CustomerType;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "customers")
@SQLRestriction("is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, referencedColumnName = "id")
    private User user;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 30)
    private CustomerType customerType;

    @Column(name = "loyalty_points", nullable = false)
    private int loyaltyPoints;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private List<Address> addresses = new ArrayList<>();

    @PrePersist
    private void ensureId() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    public static Customer create(User user, String fullName, String phone) {
        if (user == null || isBlank(fullName)) {
            throw new IllegalArgumentException("user and fullName are required");
        }
        Customer customer = new Customer();
        customer.user = user;
        customer.fullName = fullName.trim();
        customer.phone = phone;
        customer.customerType = CustomerType.REGULAR;
        customer.loyaltyPoints = 0;
        return customer;
    }

    public void updateProfile(String fullName, String phone, String avatarUrl) {
        if (isBlank(fullName)) {
            throw new IllegalArgumentException("fullName is required");
        }
        this.fullName = fullName.trim();
        this.phone = phone;
        this.avatarUrl = avatarUrl;
    }

    public void addLoyaltyPoints(int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive");
        }
        this.loyaltyPoints += points;
    }

    public void changeType(CustomerType customerType) {
        if (customerType == null) {
            throw new IllegalArgumentException("customerType is required");
        }
        this.customerType = customerType;
    }

    public Address addAddress(String label, String addressLine, String district,
                              String city, BigDecimal latitude, BigDecimal longitude, boolean defaultAddress) {
        if (defaultAddress) {
            addresses.forEach(Address::unsetDefault);
        }
        Address address = Address.create(label, addressLine, district, city, latitude, longitude, defaultAddress);
        addresses.add(address);
        return address;
    }

    public void removeAddress(UUID addressId) {
        Address target = findAddress(addressId);
        addresses.remove(target);
    }

    public void setDefaultAddress(UUID addressId) {
        Address target = findAddress(addressId);
        target.setDefault();
        addresses.forEach(a -> {
            if (!a.equals(target)) {
                a.unsetDefault();
            }
        });
    }

    private Address findAddress(UUID addressId) {
        return addresses.stream()
                .filter(a -> a.getId() != null && a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new AddressNotFoundException("Address not found: " + addressId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
