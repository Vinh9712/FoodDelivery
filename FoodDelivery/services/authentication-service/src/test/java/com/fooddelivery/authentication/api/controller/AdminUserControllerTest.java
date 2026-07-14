package com.fooddelivery.authentication.api.controller;

import com.fooddelivery.authentication.application.usecase.AdminUserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserControllerTest {

    @Test
    void listUsersBindsDefaultRequestParameters() throws Exception {
        AdminUserUseCase useCase = mock(AdminUserUseCase.class);
        when(useCase.listUsers(any())).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminUserController(useCase)).build();

        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }
}
