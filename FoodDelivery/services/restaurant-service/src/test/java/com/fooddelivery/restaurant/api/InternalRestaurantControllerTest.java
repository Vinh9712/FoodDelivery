package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.application.RestaurantService;
import com.fooddelivery.security.ResourceServerSecurityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = InternalRestaurantController.class,
        properties = "app.security.internal-service-secret=test-secret")
@Import(ResourceServerSecurityAutoConfiguration.class)
class InternalRestaurantControllerTest {

    @MockBean
    private RestaurantService service;

    @Autowired
    private MockMvc mvc;

    @Test
    void validInternalServiceSecretGetsOwnershipResponse() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        when(service.isOwner(restaurantId, ownerId)).thenReturn(true);
        mvc.perform(get("/internal/v1/restaurants/{restaurantId}/ownership/{userId}", restaurantId, ownerId)
                        .header("X-Internal-Service-Secret", "test-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.userId").value(ownerId.toString()))
                .andExpect(jsonPath("$.owner").value(true));
        verify(service).isOwner(restaurantId, ownerId);
    }

    @Test
    void missingOrWrongInternalServiceSecretIsDenied() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mvc.perform(get("/internal/v1/restaurants/{restaurantId}/ownership/{userId}", restaurantId, userId))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/restaurants/{restaurantId}/ownership/{userId}", restaurantId, userId)
                        .header("X-Internal-Service-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordinaryAuthenticatedNonServiceUserIsDenied() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        mvc.perform(get("/internal/v1/restaurants/{restaurantId}/ownership/{userId}", restaurantId, ownerId)
                        .header("Authorization", "Bearer ordinary-user-token"))
                .andExpect(status().isUnauthorized());
    }
}
