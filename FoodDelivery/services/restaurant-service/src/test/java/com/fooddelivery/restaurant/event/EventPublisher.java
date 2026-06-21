package com.fooddelivery.restaurant.event;

import com.fooddelivery.restaurant.domain.MenuItem;
import com.fooddelivery.restaurant.domain.Restaurant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String RESTAURANT_TOPIC = "restaurant-events";
    private static final String MENU_ITEM_TOPIC = "menu-item-events";

    public void publishRestaurantCreated(Restaurant restaurant) {
        RestaurantEvent event = RestaurantEvent.builder()
                .eventType("RESTAURANT_CREATED")
                .restaurantId(restaurant.getId())
                .ownerId(restaurant.getOwnerId())
                .name(restaurant.getName())
                .city(restaurant.getCity())
                .status(restaurant.getStatus().name())
                .timestamp(Instant.now())
                .build();

        sendEvent(RESTAURANT_TOPIC, restaurant.getId().toString(), event);
        log.info("Published RESTAURANT_CREATED event for restaurant: {}", restaurant.getId());
    }

    public void publishRestaurantUpdated(Restaurant restaurant) {
        RestaurantEvent event = RestaurantEvent.builder()
                .eventType("RESTAURANT_UPDATED")
                .restaurantId(restaurant.getId())
                .ownerId(restaurant.getOwnerId())
                .name(restaurant.getName())
                .city(restaurant.getCity())
                .status(restaurant.getStatus().name())
                .timestamp(Instant.now())
                .build();

        sendEvent(RESTAURANT_TOPIC, restaurant.getId().toString(), event);
        log.info("Published RESTAURANT_UPDATED event for restaurant: {}", restaurant.getId());
    }

    public void publishRestaurantDeleted(UUID restaurantId) {
        RestaurantEvent event = RestaurantEvent.builder()
                .eventType("RESTAURANT_DELETED")
                .restaurantId(restaurantId)
                .timestamp(Instant.now())
                .build();

        sendEvent(RESTAURANT_TOPIC, restaurantId.toString(), event);
        log.info("Published RESTAURANT_DELETED event for restaurant: {}", restaurantId);
    }

    public void publishMenuItemCreated(MenuItem item) {
        MenuItemEvent event = MenuItemEvent.builder()
                .eventType("MENU_ITEM_CREATED")
                .itemId(item.getId())
                .restaurantId(item.getRestaurant().getId())
                .categoryId(item.getCategory() != null ? item.getCategory().getId() : null)
                .name(item.getName())
                .price(item.getPrice())
                .isAvailable(item.getIsAvailable())
                .timestamp(Instant.now())
                .build();

        sendEvent(MENU_ITEM_TOPIC, item.getId().toString(), event);
        log.info("Published MENU_ITEM_CREATED event for item: {}", item.getId());
    }

    public void publishMenuItemUpdated(MenuItem item) {
        MenuItemEvent event = MenuItemEvent.builder()
                .eventType("MENU_ITEM_UPDATED")
                .itemId(item.getId())
                .restaurantId(item.getRestaurant().getId())
                .categoryId(item.getCategory() != null ? item.getCategory().getId() : null)
                .name(item.getName())
                .price(item.getPrice())
                .isAvailable(item.getIsAvailable())
                .timestamp(Instant.now())
                .build();

        sendEvent(MENU_ITEM_TOPIC, item.getId().toString(), event);
        log.info("Published MENU_ITEM_UPDATED event for item: {}", item.getId());
    }

    public void publishMenuItemDeleted(UUID itemId, UUID restaurantId) {
        MenuItemEvent event = MenuItemEvent.builder()
                .eventType("MENU_ITEM_DELETED")
                .itemId(itemId)
                .restaurantId(restaurantId)
                .timestamp(Instant.now())
                .build();

        sendEvent(MENU_ITEM_TOPIC, itemId.toString(), event);
        log.info("Published MENU_ITEM_DELETED event for item: {}", itemId);
    }

    public void publishMenuItemAvailabilityChanged(MenuItem item) {
        MenuItemEvent event = MenuItemEvent.builder()
                .eventType("MENU_ITEM_AVAILABILITY_CHANGED")
                .itemId(item.getId())
                .restaurantId(item.getRestaurant().getId())
                .categoryId(item.getCategory() != null ? item.getCategory().getId() : null)
                .name(item.getName())
                .price(item.getPrice())
                .isAvailable(item.getIsAvailable())
                .timestamp(Instant.now())
                .build();

        sendEvent(MENU_ITEM_TOPIC, item.getId().toString(), event);
        log.info("Published MENU_ITEM_AVAILABILITY_CHANGED event for item: {}", item.getId());
    }

    private void sendEvent(String topic, String key, Object event) {
        try {
            kafkaTemplate.send(topic, key, event);
            log.debug("Event sent to topic: {}, key: {}", topic, key);
        } catch (Exception e) {
            log.error("Failed to send event to topic: {}", topic, e);
        }
    }
}