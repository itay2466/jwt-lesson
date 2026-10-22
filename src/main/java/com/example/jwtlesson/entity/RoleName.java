package com.example.jwtlesson.entity;

/**
 * Role names. The "ROLE_" prefix is a Spring Security convention:
 * hasRole("ADMIN") in SecurityConfig looks for the authority "ROLE_ADMIN".
 */
public enum RoleName {
    ROLE_USER,
    ROLE_ADMIN
}
