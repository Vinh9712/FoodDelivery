package com.fooddelivery.authentication.domain.model;

import com.fooddelivery.commonweb.base.BaseEntity;
import com.fooddelivery.authentication.domain.model.enums.UserRole;
import com.fooddelivery.authentication.domain.model.valueobject.Email;
import com.fooddelivery.authentication.domain.model.valueobject.PhoneNumber;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
@SQLRestriction("is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private UserRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @PrePersist
    private void ensureId() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
    }
    public static User registerCustomer(String email, String phone, String passwordHash) {
        return register(email, phone, passwordHash, UserRole.CUSTOMER);
    }

    public static User register(String email, String phone, String passwordHash, UserRole role) {
        return register(new Email(email), new PhoneNumber(phone), passwordHash, role);
    }

    public static User register(Email email, PhoneNumber phone, String passwordHash, UserRole role) {
        if (isBlank(passwordHash) || role == null) {
            throw new IllegalArgumentException("password and role are required");
        }
        User user = new User();
        user.email = email.value();
        user.phone = phone.value();
        user.passwordHash = passwordHash;
        user.role = role;
        return user;
    }

    public void changePassword(String newPasswordHash) {
        if (isBlank(newPasswordHash)) {
            throw new IllegalArgumentException("password is required");
        }
        this.passwordHash = newPasswordHash;
    }

    public void updatePhone(String phone) {
        updatePhone(new PhoneNumber(phone));
    }

    public void updatePhone(PhoneNumber phone) {
        this.phone = phone.value();
    }

    public void markLoggedIn() {
        this.lastLoginAt = Instant.now();
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public void changeRole(UserRole newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("role is required");
        }
        this.role = newRole;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
