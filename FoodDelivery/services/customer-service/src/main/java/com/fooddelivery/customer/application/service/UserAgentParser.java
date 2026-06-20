package com.fooddelivery.customer.application.service;

import com.fooddelivery.customer.domain.vo.DeviceInfo;
import org.springframework.stereotype.Component;
import ua_parser.Client;
import ua_parser.Parser;

@Component
public class UserAgentParser {

    private final Parser parser;

    public UserAgentParser() {
        this.parser = new Parser();
    }

    public DeviceInfo parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new DeviceInfo("UNKNOWN", "Unknown", "Unknown", "Unknown Device");
        }

        Client client = parser.parse(userAgent);

        String deviceType = detectDeviceType(client);
        String browser = client.userAgent != null
                ? client.userAgent.family + " " + client.userAgent.major
                : "Unknown";
        String os = client.os != null
                ? client.os.family + " " + client.os.major
                : "Unknown";
        String deviceName = client.device != null
                ? client.device.family
                : "Unknown Device";

        if ("Other".equals(deviceName)) {
            deviceName = deviceType.equals("MOBILE") ? "Mobile Device" : "Computer";
        }

        return new DeviceInfo(deviceType, browser, os, deviceName);
    }

    private String detectDeviceType(Client client) {
        if (client.device != null) {
            String family = client.device.family;
            if (family != null) {
                String lower = family.toLowerCase();
                if (lower.contains("tablet") || lower.contains("ipad")) {
                    return "TABLET";
                }
                if (lower.contains("phone") || lower.contains("iphone")) {
                    return "MOBILE";
                }
                if (lower.contains("spider") || lower.contains("bot")) {
                    return "BOT";
                }
            }
        }
        if (client.os != null && client.os.family != null) {
            String os = client.os.family.toLowerCase();
            if (os.contains("android") || os.contains("ios")) {
                return "MOBILE";
            }
        }
        if (client.userAgent != null && client.userAgent.family != null) {
            String ua = client.userAgent.family.toLowerCase();
            if (ua.contains("mobile") || ua.contains("android")) return "MOBILE";
        }
        return "DESKTOP";
    }
}
