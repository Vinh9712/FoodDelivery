package com.fooddelivery.customer.api.dto.response;

import java.util.List;

public record AdminUserPageResponse(
    List<AdminUserResponse> items,
    long total
) {}
