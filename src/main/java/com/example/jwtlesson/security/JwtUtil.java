package com.example.jwtlesson.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * Creates and verifies JWT tokens (HMAC-SHA256 signature with a server-side secret).
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String base64Secret,
                   @Value("${jwt.expiration-ms}") long expirationMs) {
        // Throws WeakKeyException if the secret is shorter than 256 bits
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    /** Called after a successful login. */
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(userDetails.getUsername())                 // "sub"
                .claim("roles", roles)                              // custom claim, for the client to read
                .issuedAt(now)                                      // "iat"
                .expiration(new Date(now.getTime() + expirationMs)) // "exp"
                .signWith(key)                                      // HS256 for a 256-bit key
                .compact();                                         // header.payload.signature
    }

    /**
     * Verifies the signature and the expiry, then returns the payload.
     * Throws JwtException (SignatureException, ExpiredJwtException, MalformedJwtException ...)
     * if the token was changed, expired or is not a JWT.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
