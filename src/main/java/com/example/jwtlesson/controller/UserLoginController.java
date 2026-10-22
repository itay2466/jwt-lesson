package com.example.jwtlesson.controller;

import com.example.jwtlesson.dto.LoginRequest;
import com.example.jwtlesson.dto.LoginResponse;
import com.example.jwtlesson.dto.RegisterRequest;
import com.example.jwtlesson.dto.UserResponse;
import com.example.jwtlesson.dto.RefreshRequest;
import com.example.jwtlesson.service.RefreshTokenService;
import com.example.jwtlesson.service.UserLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoints (permitAll in SecurityConfig): login and register.
 * The controller only receives HTTP and delegates - the logic lives in UserLoginService.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserLoginController {

    private final UserLoginService userLoginService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return refreshTokenService.refresh(request.refreshToken());
    }

    // POST /api/auth/login   {"username":"admin","password":"Admin123!"}
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userLoginService.login(request);
    }

    // POST /api/auth/register   {"username":"dana","password":"Dana1234!"}
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userLoginService.register(request);
    }
}
