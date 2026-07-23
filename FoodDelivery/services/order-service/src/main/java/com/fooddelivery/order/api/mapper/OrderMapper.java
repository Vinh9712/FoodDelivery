package com.fooddelivery.order.api.mapper;

import com.fooddelivery.order.api.dto.AssignedDriverDto;
import com.fooddelivery.order.api.dto.DeliveryAddressDto;
import com.fooddelivery.order.api.dto.OrderItemDto;
import com.fooddelivery.order.api.dto.OrderResponse;
import com.fooddelivery.order.api.dto.OrderStatusHistoryDto;
import com.fooddelivery.order.domain.model.Order;
import com.fooddelivery.order.domain.model.OrderItem;
import com.fooddelivery.order.domain.model.OrderStatusHistory;
import com.fooddelivery.order.domain.model.valueobject.AssignedDriverInfo;
import com.fooddelivery.order.domain.model.valueobject.DeliveryAddressSnapshot;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Maps Order domain model to API DTOs.
 */
@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getSubtotal(),
                order.getDeliveryFee(),
                order.getDiscountAmount(),
                order.getNote(),
                mapItems(order.getItems()),
                mapAddress(order.getDeliveryAddressSnapshot()),
                order.getAssignedDriver().map(this::toDriverDto).orElse(null),
                order.getPaymentStatus(),
                order.getRefundStatus(),
                order.getCancellationCode(),
                order.getCancellationReason(),
                mapHistory(order.getStatusHistory()),
                order.getPaidAt(),
                order.getRestaurantResponseDeadline(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private List<OrderItemDto> mapItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new OrderItemDto(
                        item.getId(),
                        item.getMenuItemId(),
                        item.getItemName(),
                        item.getItemDescription(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal()))
                .toList();
    }

    private DeliveryAddressDto mapAddress(DeliveryAddressSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new DeliveryAddressDto(
                snapshot.addressLine(),
                snapshot.district(),
                snapshot.city(),
                snapshot.latitude(),
                snapshot.longitude());
    }

    private List<OrderStatusHistoryDto> mapHistory(List<OrderStatusHistory> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
                .sorted(Comparator.comparing(OrderStatusHistory::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(h -> new OrderStatusHistoryDto(
                        h.getFromStatus(),
                        h.getToStatus(),
                        h.getNote(),
                        h.getChangedBy(),
                        h.getCreatedAt()))
                .toList();
    }

    private AssignedDriverDto toDriverDto(AssignedDriverInfo info) {
        return new AssignedDriverDto(
                info.driverId(),
                info.fullName(),
                info.phone(),
                info.vehicleType().name(),
                info.licensePlate(),
                info.avgRating(),
                info.assignedAt()
        );
    }
}
