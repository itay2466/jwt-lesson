package com.example.jwtlesson.service;

import com.example.jwtlesson.dto.ChangePasswordRequest;
import com.example.jwtlesson.dto.UserResponse;
import com.example.jwtlesson.entity.*;
import com.example.jwtlesson.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;

    // Service authorization also protects callers that do not go through an HTTP controller.
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public long count() {
        return users.count();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse addRoles(Long id, Set<RoleName> requested) {
        User user = find(id);
        for (RoleName name : requested) {
            user.getRoles().add(roles.findByName(name).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role")));
        }
        return UserResponse.from(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled) {
        User user = find(id);
        user.setEnabled(enabled);
        if (!enabled) refreshTokens.revokeForUser(id);
        return UserResponse.from(user);
    }

    @PreAuthorize("hasRole('USER') and #username == authentication.name")
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = users.findByUsername(username).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!encoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BadCredentialsException("Incorrect old password");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds BCrypt's 72-byte limit");
        }
        user.setPassword(encoder.encode(request.newPassword()));
        refreshTokens.revokeForUser(user.getId());
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
