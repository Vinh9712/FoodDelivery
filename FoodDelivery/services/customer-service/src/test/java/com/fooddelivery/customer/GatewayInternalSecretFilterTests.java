package com.fooddelivery.customer;

import com.fooddelivery.customer.config.GatewayInternalSecretFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayInternalSecretFilterTests {

    @Test
    void customerRouteRejectsMissingGatewaySecret() throws Exception {
        GatewayInternalSecretFilter filter = new GatewayInternalSecretFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void customerRouteAllowsValidGatewaySecret() throws Exception {
        GatewayInternalSecretFilter filter = new GatewayInternalSecretFilter("secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers/me");
        request.addHeader(GatewayInternalSecretFilter.HEADER_NAME, "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }
}
