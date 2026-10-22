package com.example.jwtlesson.controller;

import com.example.jwtlesson.dto.UserResponse;
import com.example.jwtlesson.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Three endpoints, one per security zone in SecurityConfig.
 * Note: no security code here - the rules live only in SecurityConfig.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResourceController {

    private final UserService userService;

    // Zone 1: anyone
    @GetMapping("/public/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Public endpoint - no token needed");
    }

    // Zone 2: ROLE_USER. Spring injects the Authentication that JwtAuthFilter created.
    @GetMapping("/user/me")
    public UserResponse me(Authentication authentication) {
        return userService.getByUsername(authentication.getName());
    }

    // Zone 3: ROLE_ADMIN only
    @GetMapping("/admin/users")
    public List<UserResponse> allUsers() {
        return userService.getAll();
    }
}
