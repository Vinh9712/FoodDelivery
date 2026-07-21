package com.fooddelivery.restaurant.api;

import com.fooddelivery.restaurant.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestParam("username") String username,
            @RequestParam("role") String role) {
        log.info("Login request for user: {} with role: {}", username, role);

        String token = jwtUtil.generateToken(username, role);

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("username", username);
        response.put("role", role);
        response.put("message", "Login successful!");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/admin")
    public ResponseEntity<Map<String, String>> loginAdmin() {
        String token = jwtUtil.generateToken("admin", "ADMIN");

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("username", "admin");
        response.put("role", "ADMIN");
        response.put("message", "Admin login successful!");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login/owner")
    public ResponseEntity<Map<String, String>> loginOwner() {
        String token = jwtUtil.generateToken("owner", "RESTAURANT_OWNER");

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("username", "owner");
        response.put("role", "RESTAURANT_OWNER");
        response.put("message", "Owner login successful!");

        return ResponseEntity.ok(response);
    }
}