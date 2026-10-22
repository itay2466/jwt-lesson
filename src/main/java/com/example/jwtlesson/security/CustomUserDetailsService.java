package com.example.jwtlesson.security;

import com.example.jwtlesson.entity.User;
import com.example.jwtlesson.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridge between OUR database and Spring Security.
 * Loads a user by name and returns a UserDetails (name + password hash + roles + enabled).
 * It does NOT compare passwords - DaoAuthenticationProvider does that with the PasswordEncoder.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name())) // "ROLE_ADMIN"
                .toList();

        // Spring's own User class (full name - our entity is also called User)
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())   // the BCrypt hash from the DB
                .disabled(!user.isEnabled())
                .authorities(authorities)
                .build();
    }
}
