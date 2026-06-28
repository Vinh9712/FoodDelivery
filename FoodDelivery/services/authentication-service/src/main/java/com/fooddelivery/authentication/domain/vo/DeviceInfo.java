package com.fooddelivery.authentication.domain.vo;

public record DeviceInfo(
    String deviceType,
    String browser,
    String os,
    String deviceName
) {}
