package com.fooddelivery.customer.domain.model;

import com.fooddelivery.customer.domain.exception.AddressNotFoundException;
import com.fooddelivery.customer.domain.exception.CannotRemoveDefaultAddressException;
import com.fooddelivery.customer.domain.model.valueobject.FullName;
import com.fooddelivery.customer.domain.model.valueobject.Phone;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.fooddelivery.customer.domain.util.UuidCreator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private List<Address> addresses = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Customer(UUID id, UUID userId, FullName fullName, Phone phone, String avatarUrl) {
        this.id = id;
        this.userId = userId;
        this.fullName = fullName.value();
        this.phone = phone != null ? phone.value() : null;
        this.avatarUrl = avatarUrl;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Customer create(UUID userId, FullName fullName, Phone phone, String avatarUrl) {
        return new Customer(UuidCreator.nextUuidV7(), userId, fullName, phone, avatarUrl);
    }

    public FullName getFullName() {
        return new FullName(this.fullName);
    }

    public Phone getPhone() {
        return this.phone != null ? new Phone(this.phone) : null;
    }

    public void updateProfile(FullName fullName, Phone phone, String avatarUrl) {
        this.fullName = fullName.value();
        this.phone = phone != null ? phone.value() : null;
        this.avatarUrl = avatarUrl;
        this.updatedAt = Instant.now();
    }

    public Address addAddress(Address address) {
        if (addresses.isEmpty()) {
            address.markAsDefault();
        }
        addresses.add(address);
        this.updatedAt = Instant.now();
        return address;
    }

    public void removeAddress(UUID addressId) {
        Address target = findAddress(addressId);
        if (target.isDefault() && addresses.size() > 1) {
            throw new CannotRemoveDefaultAddressException(addressId);
        }
        addresses.remove(target);
        this.updatedAt = Instant.now();
    }

    public void setDefaultAddress(UUID addressId) {
        Address target = findAddress(addressId);
        addresses.forEach(Address::unmarkAsDefault);
        target.markAsDefault();
        this.updatedAt = Instant.now();
    }

    private Address findAddress(UUID id) {
        return addresses.stream()
                .filter(a -> a.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new AddressNotFoundException(id));
    }
}
