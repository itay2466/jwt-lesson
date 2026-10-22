package com.example.jwtlesson.controller;

import com.example.jwtlesson.dto.*;
import com.example.jwtlesson.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserManagementController {
    private final UserManagementService users;

    @GetMapping("/admin/stats")
    public Map<String, Long> stats() {
        return Map.of("userCount", users.count());
    }

    @PutMapping("/admin/users/{id}/roles")
    public UserResponse addRoles(@PathVariable Long id, @Valid @RequestBody AddRolesRequest request) {
        return users.addRoles(id, request.roles());
    }

    @PutMapping("/admin/users/{id}/enabled")
    public UserResponse enabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return users.setEnabled(id, request.enabled());
    }

    @PutMapping("/user/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        users.changePassword(authentication.getName(), request);
    }
}
