package com.fooddelivery.restaurant.application;

import com.fooddelivery.restaurant.api.dto.internal.MenuQuoteRequest;
import com.fooddelivery.restaurant.domain.MenuItem;
import com.fooddelivery.restaurant.domain.MenuItemRepository;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.domain.RestaurantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MenuQuoteServiceTest {

    private static final Clock OPEN_CLOCK = Clock.fixed(
            Instant.parse("2026-07-22T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    private MenuQuoteService menuQuoteService;
    private UUID restaurantId;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        menuQuoteService = new MenuQuoteService(restaurantRepository, menuItemRepository, OPEN_CLOCK);
        restaurantId = UUID.randomUUID();
        restaurant = activeRestaurant();
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    }

    @Test
    void quote_usesDatabasePriceAndAggregatesDuplicateQuantities() {
        UUID itemId = UUID.randomUUID();
        MenuItem item = menuItem(itemId, restaurant, "Pho", "75000", null, true);
        when(menuItemRepository.findAllById(anyIterable())).thenReturn(List.of(item));

        var response = menuQuoteService.quote(restaurantId, new MenuQuoteRequest(List.of(
                new MenuQuoteRequest.Item(itemId, 1),
                new MenuQuoteRequest.Item(itemId, 2))));

        assertEquals(new BigDecimal("225000"), response.subtotal());
        assertEquals(1, response.items().size());
        assertEquals(new BigDecimal("75000"), response.items().getFirst().unitPrice());
        assertEquals(3, response.items().getFirst().quantity());
        assertEquals(new BigDecimal("225000"), response.items().getFirst().lineTotal());
    }

    @Test
    void quote_appliesOnlyValidServerSideDiscountPrice() {
        UUID itemId = UUID.randomUUID();
        MenuItem item = menuItem(itemId, restaurant, "Pho", "75000", "60000", true);
        when(menuItemRepository.findAllById(anyIterable())).thenReturn(List.of(item));

        var response = menuQuoteService.quote(restaurantId,
                new MenuQuoteRequest(List.of(new MenuQuoteRequest.Item(itemId, 2))));

        assertEquals(new BigDecimal("60000"), response.items().getFirst().unitPrice());
        assertEquals(new BigDecimal("120000"), response.subtotal());
    }

    @Test
    void quote_rejectsItemOwnedByAnotherRestaurant() {
        UUID itemId = UUID.randomUUID();
        Restaurant otherRestaurant = Restaurant.builder().id(UUID.randomUUID()).build();
        MenuItem item = menuItem(itemId, otherRestaurant, "Pho", "75000", null, true);
        when(menuItemRepository.findAllById(anyIterable())).thenReturn(List.of(item));

        assertThrows(IllegalArgumentException.class, () -> menuQuoteService.quote(
                restaurantId, new MenuQuoteRequest(List.of(new MenuQuoteRequest.Item(itemId, 1)))));
    }

    @Test
    void quote_rejectsUnavailableOrMissingItem() {
        UUID unavailableId = UUID.randomUUID();
        MenuItem unavailable = menuItem(unavailableId, restaurant, "Pho", "75000", null, false);
        when(menuItemRepository.findAllById(anyIterable())).thenReturn(List.of(unavailable), List.of());

        assertThrows(IllegalArgumentException.class, () -> menuQuoteService.quote(
                restaurantId, new MenuQuoteRequest(List.of(new MenuQuoteRequest.Item(unavailableId, 1)))));

        UUID missingId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> menuQuoteService.quote(
                restaurantId, new MenuQuoteRequest(List.of(new MenuQuoteRequest.Item(missingId, 1)))));
    }

    @Test
    void quote_rejectsSubtotalBelowRestaurantMinimum() {
        restaurant.setMinOrderAmount(new BigDecimal("100000"));
        UUID itemId = UUID.randomUUID();
        MenuItem item = menuItem(itemId, restaurant, "Tea", "5000", null, true);
        when(menuItemRepository.findAllById(anyIterable())).thenReturn(List.of(item));

        assertThrows(IllegalArgumentException.class, () -> menuQuoteService.quote(
                restaurantId, new MenuQuoteRequest(List.of(new MenuQuoteRequest.Item(itemId, 1)))));
    }

    @Test
    void quote_rejectsCombinedQuantityAboveLimit() {
        UUID itemId = UUID.randomUUID();
        var request = new MenuQuoteRequest(List.of(
                new MenuQuoteRequest.Item(itemId, 60),
                new MenuQuoteRequest.Item(itemId, 40)));

        assertThrows(IllegalArgumentException.class, () -> menuQuoteService.quote(restaurantId, request));
    }

    @Test
    void quoteRejectsInactiveRestaurantBeforeLoadingMenuItems() {
        restaurant.setStatus(RestaurantStatus.INACTIVE);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> menuQuoteService.quote(restaurantId, request()));

        assertEquals("Restaurant is not accepting orders", exception.getMessage());
        verifyNoInteractions(menuItemRepository);
    }

    @Test
    void quoteRejectsRestaurantOutsideHoursBeforeLoadingMenuItems() {
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setOpenTime(LocalTime.of(20, 0));
        restaurant.setCloseTime(LocalTime.of(22, 0));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> menuQuoteService.quote(restaurantId, request()));

        assertEquals("Restaurant is not accepting orders", exception.getMessage());
        verifyNoInteractions(menuItemRepository);
    }

    @Test
    void quoteReturnsImmutablePickupSnapshot() {
        restaurant.setName("Pho 24");
        restaurant.setPhone("0901000000");
        restaurant.setAddressLine("12 Le Loi");
        restaurant.setDistrict("District 1");
        restaurant.setCity("Ho Chi Minh City");
        UUID itemId = UUID.randomUUID();
        when(menuItemRepository.findAllById(anyIterable())).thenReturn(List.of(
                menuItem(itemId, restaurant, "Pho", "75000", null, true)));

        var response = menuQuoteService.quote(restaurantId, request(itemId));

        assertEquals(new com.fooddelivery.restaurant.api.dto.internal.MenuQuoteResponse.PickupSnapshot(
                restaurantId, "Pho 24", "0901000000", "12 Le Loi, District 1, Ho Chi Minh City", null, null),
                response.pickup());
    }

    private Restaurant activeRestaurant() {
        return Restaurant.builder()
                .id(restaurantId)
                .status(RestaurantStatus.ACTIVE)
                .isAcceptingOrders(true)
                .openTime(LocalTime.of(8, 0))
                .closeTime(LocalTime.of(22, 0))
                .minOrderAmount(BigDecimal.ZERO)
                .build();
    }

    private MenuQuoteRequest request() {
        return new MenuQuoteRequest(List.of(new MenuQuoteRequest.Item(UUID.randomUUID(), 1)));
    }

    private MenuQuoteRequest request(UUID itemId) {
        return new MenuQuoteRequest(List.of(new MenuQuoteRequest.Item(itemId, 1)));
    }

    private MenuItem menuItem(UUID id, Restaurant owner, String name, String price,
                              String discountPrice, boolean available) {
        return MenuItem.builder()
                .id(id)
                .restaurant(owner)
                .name(name)
                .description(name + " description")
                .price(new BigDecimal(price))
                .discountPrice(discountPrice == null ? null : new BigDecimal(discountPrice))
                .isAvailable(available)
                .build();
    }
}
