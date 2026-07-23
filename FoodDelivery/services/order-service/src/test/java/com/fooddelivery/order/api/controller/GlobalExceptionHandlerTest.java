package com.fooddelivery.order.api.controller;

import feign.FeignException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void invalidRequestBody_ShouldKeepFrameworkBadRequestMapping() throws Exception {
        mockMvc.perform(post("/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feignFailure_ShouldNotExposeExceptionMessage() {
        FeignException exception = mock(FeignException.class);
        when(exception.status()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE.value());
        when(exception.getMessage()).thenReturn("POST http://payment-service:8085/internal/payments: secret-body");

        ProblemDetail problem = handler.handleFeignException(exception);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), problem.getStatus());
        assertEquals("Downstream microservice call failed", problem.getDetail());
    }

    @RestController
    private static class ValidationController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody ValidationRequest request) {
        }
    }

    private record ValidationRequest(@NotBlank String value) {
    }
}
