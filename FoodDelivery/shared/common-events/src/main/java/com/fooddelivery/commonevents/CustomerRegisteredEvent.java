package com.fooddelivery.commonevents;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class CustomerRegisteredEvent extends BaseEvent {
    private UUID customerId;
    private UUID userId;
    private String email;
    private String phone;
    private String fullName;
    private String role;

    public CustomerRegisteredEvent() {
        super("customer.created", 1);
    }

    public CustomerRegisteredEvent(UUID customerId, UUID userId, String email, String phone, String fullName, String role) {
        super("customer.created", 1);
        this.customerId = customerId;
        this.userId = userId;
        this.email = email;
        this.phone = phone;
        this.fullName = fullName;
        this.role = role;
    }
}
