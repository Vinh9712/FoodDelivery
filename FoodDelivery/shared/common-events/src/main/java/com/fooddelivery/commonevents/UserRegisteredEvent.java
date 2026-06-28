package com.fooddelivery.commonevents;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class UserRegisteredEvent extends BaseEvent {
    private UUID userId;
    private String email;
    private String phone;
    private String fullName;
    private String role;

    public UserRegisteredEvent() {
        super("user.registered", 1);
    }

    public UserRegisteredEvent(UUID userId, String email, String phone, String fullName, String role) {
        super("user.registered", 1);
        this.userId = userId;
        this.email = email;
        this.phone = phone;
        this.fullName = fullName;
        this.role = role;
    }
}
