package com.example.jwtlesson.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No "roles" field on purpose: a client must never choose its own role
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 8, max = 72) String password) { // BCrypt uses at most 72 bytes
}
