package com.fooddelivery.restaurant.domain;

import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RestaurantOptimisticLockingTest {

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Test
    void restaurantUsesVersionColumnForConcurrentStatusAndAvailabilityUpdates() throws Exception {
        var version = Restaurant.class.getDeclaredField("version");

        assertThat(version.getType()).isEqualTo(Long.class);
        assertThat(version.getAnnotation(Version.class)).isNotNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void staleRestaurantUpdateIsRejectedWithoutLosingCommittedStatusChange() {
        Restaurant created = restaurantRepository.saveAndFlush(Restaurant.builder()
                .ownerId(UUID.randomUUID())
                .name("Lock test")
                .phone("0900000000")
                .addressLine("Address")
                .city("City")
                .status(RestaurantStatus.ACTIVE)
                .openTime(LocalTime.of(8, 0))
                .closeTime(LocalTime.of(22, 0))
                .avgRating(BigDecimal.ZERO)
                .totalReviews(0)
                .minOrderAmount(BigDecimal.ZERO)
                .estimatedDeliveryTimeMin(30)
                .isAcceptingOrders(true)
                .build());
        Long initialVersion = created.getVersion();

        Restaurant firstCopy = restaurantRepository.findById(created.getId()).orElseThrow();
        Restaurant staleCopy = restaurantRepository.findById(created.getId()).orElseThrow();

        firstCopy.changeStatus(RestaurantStatus.INACTIVE);
        Restaurant committed = restaurantRepository.saveAndFlush(firstCopy);

        staleCopy.setAcceptingOrders(false);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> restaurantRepository.saveAndFlush(staleCopy)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        Restaurant current = restaurantRepository.findById(created.getId()).orElseThrow();
        assertThat(committed.getVersion()).isGreaterThan(initialVersion);
        assertThat(current.getStatus()).isEqualTo(RestaurantStatus.INACTIVE);
        assertThat(current.getIsAcceptingOrders()).isFalse();
    }
}
