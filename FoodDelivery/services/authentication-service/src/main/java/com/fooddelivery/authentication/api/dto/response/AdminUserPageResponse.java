package com.fooddelivery.authentication.api.dto.response;

import java.util.List;

public record AdminUserPageResponse(
    List<AdminUserResponse> items,
    long total
) {}
