package com.fooddelivery.restaurant.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantEligibilityTest {

    @Test
    void acceptsOnlyActiveEnabledRestaurantInsideNormalHours() {
        Restaurant restaurant = Restaurant.builder()
                .status(RestaurantStatus.ACTIVE).isAcceptingOrders(true)
                .openTime(LocalTime.of(8, 0)).closeTime(LocalTime.of(22, 0)).build();

        assertThat(restaurant.canAcceptOrders(LocalTime.of(8, 0))).isTrue();
        assertThat(restaurant.canAcceptOrders(LocalTime.of(21, 59))).isTrue();
        assertThat(restaurant.canAcceptOrders(LocalTime.of(22, 0))).isFalse();

        restaurant.setStatus(RestaurantStatus.INACTIVE);
        assertThat(restaurant.canAcceptOrders(LocalTime.NOON)).isFalse();
    }

    @Test
    void supportsOvernightAndTwentyFourHourWindows() {
        Restaurant overnight = Restaurant.builder().status(RestaurantStatus.ACTIVE)
                .isAcceptingOrders(true).openTime(LocalTime.of(20, 0)).closeTime(LocalTime.of(3, 0)).build();
        Restaurant always = Restaurant.builder().status(RestaurantStatus.ACTIVE)
                .isAcceptingOrders(true).openTime(LocalTime.NOON).closeTime(LocalTime.NOON).build();

        assertThat(overnight.canAcceptOrders(LocalTime.of(1, 0))).isTrue();
        assertThat(overnight.canAcceptOrders(LocalTime.of(12, 0))).isFalse();
        assertThat(always.canAcceptOrders(LocalTime.of(4, 0))).isTrue();
    }
}
