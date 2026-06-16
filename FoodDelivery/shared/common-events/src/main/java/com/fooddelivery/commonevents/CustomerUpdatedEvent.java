package com.fooddelivery.commonevents;

import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class CustomerUpdatedEvent extends BaseEvent {
    private UUID customerId;
    private List<String> changedFields;
    private String fullName;
    private String phone;
    private String avatarUrl;

    public CustomerUpdatedEvent() {
        super("customer.updated", 1);
    }

    public CustomerUpdatedEvent(UUID customerId, List<String> changedFields, String fullName, String phone, String avatarUrl) {
        super("customer.updated", 1);
        this.customerId = customerId;
        this.changedFields = changedFields;
        this.fullName = fullName;
        this.phone = phone;
        this.avatarUrl = avatarUrl;
    }
}
