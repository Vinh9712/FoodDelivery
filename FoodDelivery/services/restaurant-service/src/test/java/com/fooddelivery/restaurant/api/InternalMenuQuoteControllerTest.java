package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.internal.MenuQuoteResponse;
import com.fooddelivery.restaurant.application.MenuQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalMenuQuoteControllerTest {

    @Test
    void quoteBindsRestaurantIdPathVariable() throws Exception {
        MenuQuoteService service = mock(MenuQuoteService.class);
        UUID restaurantId = UUID.randomUUID();
        when(service.quote(eq(restaurantId), any()))
                .thenReturn(new MenuQuoteResponse(restaurantId, BigDecimal.TEN, List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new InternalMenuQuoteController(service)).build();

        mvc.perform(post("/internal/v1/restaurants/{restaurantId}/menu/quote", restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"menuItemId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());
    }
}
