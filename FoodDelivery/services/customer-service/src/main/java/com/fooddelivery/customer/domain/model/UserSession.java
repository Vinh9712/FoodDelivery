package com.fooddelivery.customer.domain.model;

import com.fooddelivery.commonweb.base.BaseEntity;
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
@Table(name = "user_sessions")
@SQLRestriction("is_deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession extends BaseEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @Column(name = "browser", length = 100)
    private String browser;

    @Column(name = "os", length = 100)
    private String os;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent;

    @PrePersist
    private void ensureId() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    public static UserSession create(User user, String deviceName, String deviceType,
                                      String browser, String os, String ipAddress) {
        UserSession session = new UserSession();
        session.user = user;
        session.deviceName = deviceName;
        session.deviceType = deviceType;
        session.browser = browser;
        session.os = os;
        session.ipAddress = ipAddress;
        session.lastUsedAt = Instant.now();
        session.isCurrent = true;
        return session;
    }

    public void markUsed(String ipAddress) {
        this.lastUsedAt = Instant.now();
        this.ipAddress = ipAddress;
    }

    public void markCurrent() {
        this.isCurrent = true;
    }

    public void unmarkCurrent() {
        this.isCurrent = false;
    }
}
