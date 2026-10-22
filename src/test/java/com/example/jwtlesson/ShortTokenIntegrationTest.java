package com.example.jwtlesson;

import com.example.jwtlesson.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "short-token"})
class ShortTokenIntegrationTest {
    @LocalServerPort int port;

    @Test void sixtySecondProfileAndExpiredTokenReturn401() throws Exception {
        var client = HttpClient.newHttpClient();
        String base = "http://localhost:"+port;
        var login = client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"user\",\"password\":\"User123!\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode());
        Map<?, ?> body = JsonMapper.builder().build().readValue(login.body(), Map.class);
        assertEquals(60000, ((Number) body.get("expiresInMs")).longValue());
        // A correctly signed but already expired token exercises the same expiry branch without a slow sleep.
        var jwt = new JwtUtil("47o9ZwPSwJH0HIGIpS8wuGOJOLfiN40HPSvI/k+kv88=", -60000);
        String expired = jwt.generateToken(User.withUsername("user").password("unused").roles("USER").build());
        var response = client.send(HttpRequest.newBuilder(URI.create(base+"/api/user/me"))
                .header("Authorization", "Bearer "+expired).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
    }
}
