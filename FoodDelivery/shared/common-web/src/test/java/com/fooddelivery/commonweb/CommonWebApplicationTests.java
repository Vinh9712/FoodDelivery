package com.fooddelivery.commonweb;

import com.fooddelivery.commonweb.exception.BusinessRuleException;
import com.fooddelivery.commonweb.exception.NotFoundException;
import com.fooddelivery.commonweb.response.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommonWebApplicationTests {

    @Test
    void commonWebTypesAreUsable() {
        ApiResponse<String> response = ApiResponse.ok("ok");
        assertEquals("ok", response.getData());
        assertEquals("Success", response.getMessage());
        assertEquals("not found", new NotFoundException("not found").getMessage());
        assertEquals("rule", new BusinessRuleException("rule").getMessage());
    }
}