package com.fooddelivery.authentication.api.dto.response;

import java.util.Map;

public record DashboardStatsResponse(
    long totalUsers,
    long activeUsers,
    Map<String, Long> usersByRole
) {}
