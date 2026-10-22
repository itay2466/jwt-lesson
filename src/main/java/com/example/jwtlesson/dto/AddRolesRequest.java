package com.example.jwtlesson.dto;

import com.example.jwtlesson.entity.RoleName;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AddRolesRequest(@NotEmpty Set<@NotNull RoleName> roles) {}
