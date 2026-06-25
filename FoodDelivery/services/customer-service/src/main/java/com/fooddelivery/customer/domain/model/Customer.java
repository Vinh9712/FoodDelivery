package com.fooddelivery.customer.domain.model;

import com.fooddelivery.commonweb.base.BaseEntity;
import com.fooddelivery.customer.domain.exception.AddressNotFoundException;
import com.fooddelivery.customer.domain.model.enums.CustomerType;
import com.fooddelivery.customer.domain.model.valueobject.FullName;
import com.fooddelivery.customer.domain.model.valueobject.PhoneNumber;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

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
    private final List<Address> addresses = new ArrayList<>();

    @PrePersist
    private void ensureId() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    public static Customer create(UUID userId, String fullName, String phone) {
        return create(userId, new FullName(fullName), phone != null ? new PhoneNumber(phone) : null);
    }

    public static Customer create(UUID userId, FullName fullName, PhoneNumber phone) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        Customer customer = new Customer();
        customer.userId = userId;
        customer.fullName = fullName.value();
        customer.phone = phone != null ? phone.value() : null;
        customer.customerType = CustomerType.REGULAR;
        customer.loyaltyPoints = 0;
        return customer;
    }

    public void updateProfile(String fullName, String phone, String avatarUrl) {
        updateProfile(new FullName(fullName), phone != null ? new PhoneNumber(phone) : null, avatarUrl);
    }

    public void updateProfile(FullName fullName, PhoneNumber phone, String avatarUrl) {
        this.fullName = fullName.value();
        this.phone = phone != null ? phone.value() : null;
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
        boolean makeDefault = defaultAddress || activeAddresses().isEmpty();
        if (makeDefault) {
            activeAddresses().forEach(Address::unsetDefault);
        }
        Address address = Address.create(label, addressLine, district, city, latitude, longitude, makeDefault);
        addresses.add(address);
        return address;
    }

    public List<Address> getAddresses() {
        return Collections.unmodifiableList(addresses);
    }

    public List<Address> getActiveAddresses() {
        return Collections.unmodifiableList(activeAddresses());
    }

    public Address updateAddress(UUID addressId, String label, String addressLine, String district,
                                 String city, BigDecimal latitude, BigDecimal longitude, boolean defaultAddress) {
        Address address = findActiveAddress(addressId);
        address.update(label, addressLine, district, city, latitude, longitude);
        if (defaultAddress) {
            setDefaultAddress(addressId);
        } else if (address.isDefaultAddress() && activeAddresses().size() > 1) {
            address.unsetDefault();
            ensureOneDefaultAddress(address);
        }
        return address;
    }

    public void removeAddress(UUID addressId) {
        Address target = findActiveAddress(addressId);
        boolean wasDefault = target.isDefaultAddress();
        target.softDelete();
        if (wasDefault) {
            target.unsetDefault();
            ensureOneDefaultAddress();
        }
    }

    public void setDefaultAddress(UUID addressId) {
        Address target = findActiveAddress(addressId);
        target.setDefault();
        activeAddresses().forEach(a -> {
            if (!a.equals(target)) {
                a.unsetDefault();
            }
        });
    }

    public Address findActiveAddress(UUID addressId) {
        return activeAddresses().stream()
                .filter(a -> a.getId() != null && a.getId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    private List<Address> activeAddresses() {
        return addresses.stream()
                .filter(address -> !address.isDeleted())
                .toList();
    }

    private void ensureOneDefaultAddress() {
        ensureOneDefaultAddress(null);
    }

    private void ensureOneDefaultAddress(Address excludedAddress) {
        List<Address> active = activeAddresses();
        if (!active.isEmpty() && active.stream().noneMatch(Address::isDefaultAddress)) {
            active.stream()
                    .filter(address -> !address.equals(excludedAddress))
                    .findFirst()
                    .ifPresent(Address::setDefault);
        }
    }
}
