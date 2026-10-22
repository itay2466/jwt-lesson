package com.example.jwtlesson;

import com.example.jwtlesson.security.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import static org.junit.jupiter.api.Assertions.*;

class JwtExpiryTest {
    @Test void expiredSignedAccessTokenIsRejected() {
        var jwt = new JwtUtil("47o9ZwPSwJH0HIGIpS8wuGOJOLfiN40HPSvI/k+kv88=", -60000);
        var token = jwt.generateToken(User.withUsername("user").password("unused").roles("USER").build());
        assertThrows(ExpiredJwtException.class, () -> jwt.parseClaims(token));
    }
}
