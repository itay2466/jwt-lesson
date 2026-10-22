package com.example.jwtlesson.config;

import com.example.jwtlesson.entity.Role;
import com.example.jwtlesson.entity.RoleName;
import com.example.jwtlesson.entity.User;
import com.example.jwtlesson.repository.RoleRepository;
import com.example.jwtlesson.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs once when the application starts.
 * Checks the DB: creates the roles and the initial admin/user ONLY if they are missing.
 * Safe to run on every start (idempotent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin.username}")
    private String adminUsername;
    @Value("${app.init.admin.password}")
    private String adminPassword;
    @Value("${app.init.user.username}")
    private String userUsername;
    @Value("${app.init.user.password}")
    private String userPassword;

    @Override
    @Transactional
    public void run(String... args) {
        Role adminRole = ensureRole(RoleName.ROLE_ADMIN);
        Role userRole = ensureRole(RoleName.ROLE_USER);

        // admin has two roles, ROLE_USER has two users -> many-to-many in action
        ensureUser(adminUsername, adminPassword, Set.of(adminRole, userRole));
        ensureUser(userUsername, userPassword, Set.of(userRole));
    }

    private Role ensureRole(RoleName name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            log.info("Role {} not found - creating it", name);
            return roleRepository.save(Role.builder().name(name).build());
        });
    }

    private void ensureUser(String username, String rawPassword, Set<Role> roles) {
        if (userRepository.existsByUsername(username)) {
            log.info("User '{}' already exists - skipping", username);
            return;
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword)) // store the HASH only
                .roles(new HashSet<>(roles))
                .build();
        userRepository.save(user);
        log.info("User '{}' created with roles {}", username, roles);
    }
}
