package com.example.jwtlesson.service;

import com.example.jwtlesson.dto.LoginResponse;
import com.example.jwtlesson.entity.RefreshToken;
import com.example.jwtlesson.entity.User;
import com.example.jwtlesson.repository.RefreshTokenRepository;
import com.example.jwtlesson.repository.UserRepository;
import com.example.jwtlesson.security.CustomUserDetailsService;
import com.example.jwtlesson.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final CustomUserDetailsService details;
    private final JwtUtil jwt;
    private final long refreshExpirationMs;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, UserRepository users,
                               CustomUserDetailsService details, JwtUtil jwt,
                               @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs) {
        this.tokens = tokens;
        this.users = users;
        this.details = details;
        this.jwt = jwt;
        if (refreshExpirationMs <= 0) throw new IllegalArgumentException("Refresh expiry must be positive");
        this.refreshExpirationMs = refreshExpirationMs;
    }

    @Transactional
    public LoginResponse issue(UserDetails principal) {
        User user = users.findByUsername(principal.getUsername()).orElseThrow(this::invalid);
        if (!user.isEnabled()) throw invalid();
        return issueFor(user);
    }

    @Transactional
    public LoginResponse refresh(String rawToken) {
        RefreshToken token = tokens.findForUpdate(hash(rawToken)).orElseThrow(this::invalid);
        if (token.isRevoked() || !token.getExpiresAt().isAfter(Instant.now()) || !token.getUser().isEnabled()) {
            throw invalid();
        }
        token.setRevoked(true);
        return issueFor(token.getUser());
    }

    private LoginResponse issueFor(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash(raw));
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusMillis(refreshExpirationMs));
        tokens.save(token);
        UserDetails principal = details.loadUserByUsername(user.getUsername());
        return new LoginResponse(jwt.generateToken(principal), "Bearer", jwt.getExpirationMs(),
                principal.getUsername(), principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList(), raw, refreshExpirationMs);
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("Invalid refresh token");
    }
}
