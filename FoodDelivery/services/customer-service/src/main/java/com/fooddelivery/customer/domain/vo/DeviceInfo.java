package com.fooddelivery.customer.domain.vo;

public record DeviceInfo(
    String deviceType,
    String browser,
    String os,
    String deviceName
) {}
