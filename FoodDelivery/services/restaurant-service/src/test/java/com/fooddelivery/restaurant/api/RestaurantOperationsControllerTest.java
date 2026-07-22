package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.api.dto.RestaurantResponse;
import com.fooddelivery.restaurant.application.RestaurantService;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantRepository;
import com.fooddelivery.restaurant.domain.RestaurantStatus;
import com.fooddelivery.restaurant.domain.exception.InvalidRestaurantStateException;
import com.fooddelivery.restaurant.security.RestaurantAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
@Import(RestaurantOperationsControllerTest.SecurityConfiguration.class)
class RestaurantOperationsControllerTest {

    @MockBean
    private RestaurantService restaurantService;

    @MockBean
    private RestaurantRepository restaurantRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.test.web.servlet.MockMvc mvc;

    @Test
    void activationRequiresBusinessHoursAndSuspensionReturnsThroughInactive() {
        Restaurant restaurant = Restaurant.builder().status(RestaurantStatus.PENDING).build();

        assertThatThrownBy(() -> restaurant.changeStatus(RestaurantStatus.ACTIVE))
                .isInstanceOf(InvalidRestaurantStateException.class);

        restaurant.setOpenTime(LocalTime.of(8, 0));
        restaurant.setCloseTime(LocalTime.of(22, 0));
        restaurant.changeStatus(RestaurantStatus.ACTIVE);
        restaurant.changeStatus(RestaurantStatus.SUSPENDED);
        assertThatThrownBy(() -> restaurant.changeStatus(RestaurantStatus.ACTIVE))
                .isInstanceOf(InvalidRestaurantStateException.class);
        restaurant.changeStatus(RestaurantStatus.INACTIVE);
        restaurant.changeStatus(RestaurantStatus.ACTIVE);

        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
    }

    @Test
    void statusTransitionsAreIdempotentAndDeactivateAvailability() {
        Restaurant restaurant = Restaurant.builder()
                .status(RestaurantStatus.ACTIVE)
                .isAcceptingOrders(true)
                .openTime(LocalTime.of(8, 0))
                .closeTime(LocalTime.of(22, 0))
                .build();

        restaurant.changeStatus(RestaurantStatus.ACTIVE);
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(restaurant.getIsAcceptingOrders()).isTrue();

        restaurant.changeStatus(RestaurantStatus.INACTIVE);
        assertThat(restaurant.getIsAcceptingOrders()).isFalse();
        assertThatThrownBy(() -> restaurant.setAcceptingOrders(true))
                .isInstanceOf(InvalidRestaurantStateException.class);
        assertThatThrownBy(() -> restaurant.changeStatus(null))
                .isInstanceOf(InvalidRestaurantStateException.class);
        assertThatThrownBy(() -> restaurant.changeStatus(RestaurantStatus.PENDING))
                .isInstanceOf(InvalidRestaurantStateException.class);
    }

    @Test
    void ownerCanChangeAvailabilityOnOwnRestaurant() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRepository.existsByIdAndOwnerId(restaurantId, ownerId)).thenReturn(true);
        when(restaurantService.setAvailability(restaurantId, true)).thenReturn(response(restaurantId));

        mvc.perform(patch("/api/v1/restaurants/{id}/availability", restaurantId)
                        .header("X-Test-User", ownerId)
                        .header("X-Test-Role", "RESTAURANT_OWNER")
                        .contentType("application/json")
                        .content("{\"accepting\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidAvailabilityStateReturnsConflictErrorResponse() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRepository.existsByIdAndOwnerId(restaurantId, ownerId)).thenReturn(true);
        when(restaurantService.setAvailability(restaurantId, true))
                .thenThrow(new InvalidRestaurantStateException("Only ACTIVE restaurants can accept orders"));

        mvc.perform(patch("/api/v1/restaurants/{id}/availability", restaurantId)
                        .header("X-Test-User", ownerId)
                        .header("X-Test-Role", "RESTAURANT_OWNER")
                        .contentType("application/json")
                        .content("{\"accepting\":true}"))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(409))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Conflict"));
    }

    @Test
    void optimisticLockConflictReturnsStructuredConflictErrorResponse() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRepository.existsByIdAndOwnerId(restaurantId, ownerId)).thenReturn(true);
        when(restaurantService.setAvailability(restaurantId, true))
                .thenThrow(new OptimisticLockingFailureException("stale update"));

        mvc.perform(patch("/api/v1/restaurants/{id}/availability", restaurantId)
                        .header("X-Test-User", ownerId)
                        .header("X-Test-Role", "RESTAURANT_OWNER")
                        .contentType("application/json")
                        .content("{\"accepting\":true}"))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(409))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Conflict"));
    }

    @Test
    void missingAvailabilityIsRejected() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRepository.existsByIdAndOwnerId(restaurantId, ownerId)).thenReturn(true);

        mvc.perform(patch("/api/v1/restaurants/{id}/availability", restaurantId)
                        .header("X-Test-User", ownerId)
                        .header("X-Test-Role", "RESTAURANT_OWNER")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonOwnerGetsForbiddenWhenChangingAvailability() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRepository.existsByIdAndOwnerId(eq(restaurantId), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        mvc.perform(patch("/api/v1/restaurants/{id}/availability", restaurantId)
                        .header("X-Test-User", UUID.randomUUID())
                        .header("X-Test-Role", "RESTAURANT_OWNER")
                        .contentType("application/json")
                        .content("{\"accepting\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerGetsForbiddenWhenChangingStatus() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();

        mvc.perform(patch("/api/v1/restaurants/{id}/status", restaurantId)
                        .header("X-Test-User", ownerId)
                        .header("X-Test-Role", "RESTAURANT_OWNER")
                        .contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanChangeStatus() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(restaurantService.changeStatus(restaurantId, RestaurantStatus.ACTIVE)).thenReturn(response(restaurantId));

        mvc.perform(patch("/api/v1/restaurants/{id}/status", restaurantId)
                        .header("X-Test-User", UUID.randomUUID())
                        .header("X-Test-Role", "ADMIN")
                        .contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void malformedStatusReturnsBadRequestErrorResponse() throws Exception {
        UUID restaurantId = UUID.randomUUID();

        mvc.perform(patch("/api/v1/restaurants/{id}/status", restaurantId)
                        .header("X-Test-User", UUID.randomUUID())
                        .header("X-Test-Role", "ADMIN")
                        .contentType("application/json")
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(400))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void accessDeniedReturnsForbiddenErrorResponse() throws Exception {
        UUID restaurantId = UUID.randomUUID();

        mvc.perform(patch("/api/v1/restaurants/{id}/status", restaurantId)
                        .header("X-Test-User", UUID.randomUUID())
                        .header("X-Test-Role", "RESTAURANT_OWNER")
                        .contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(403))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Forbidden"));
    }

    private RestaurantResponse response(UUID restaurantId) {
        return RestaurantResponse.builder().id(restaurantId).status(RestaurantStatus.ACTIVE.name()).build();
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class SecurityConfiguration {

        @Bean("restaurantAuthorization")
        RestaurantAuthorizationService restaurantAuthorization(RestaurantRepository restaurantRepository) {
            return new RestaurantAuthorizationService(restaurantRepository, mock(), mock());
        }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .addFilterBefore(new TestAuthenticationFilter(),
                            org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class)
                    .build();
        }
    }

    static class TestAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String subject = request.getHeader("X-Test-User");
            String role = request.getHeader("X-Test-Role");
            if (subject != null && role != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                subject, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
            }
            chain.doFilter(request, response);
        }
    }
}
