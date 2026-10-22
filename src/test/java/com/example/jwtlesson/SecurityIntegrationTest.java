package com.example.jwtlesson;

import com.example.jwtlesson.entity.RefreshToken;
import com.example.jwtlesson.repository.RefreshTokenRepository;
import com.example.jwtlesson.repository.UserRepository;
import com.example.jwtlesson.service.UserManagementService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP requests exercise the complete filter chain, controllers, services and database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityIntegrationTest {
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired UserManagementService management;
    final HttpClient http = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();

    record Result(int status, Map<String, Object> body) {
        String text(String key) { return (String) body.get(key); }
    }

    Result request(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = response.body().isBlank() || response.body().startsWith("[")
                ? Map.of() : json.readValue(response.body(), Map.class);
        return new Result(response.statusCode(), parsed);
    }

    Result login(String username, String password) throws Exception {
        return request("POST", "/api/auth/login", null, Map.of("username", username, "password", password));
    }

    String student() throws Exception {
        String name = "test_" + UUID.randomUUID().toString().replace("-", "");
        var result = request("POST", "/api/auth/register", null,
                Map.of("username", name, "password", "Student123!"));
        assertEquals(201, result.status());
        assertFalse(result.body().containsKey("password"));
        return name;
    }

    @Test void basicSecurityAndValidation() throws Exception {
        assertEquals(200, request("GET", "/api/public/hello", null, null).status());
        assertEquals(401, request("GET", "/api/user/me", null, null).status());
        assertEquals(401, request("GET", "/api/user/me", "garbage", null).status());
        assertEquals(401, login("admin", "wrong").status());
        assertEquals(login("admin", "wrong").body(), login("absent", "wrong").body());
        assertEquals(400, login("", "").status());
        String userToken = login("user", "User123!").text("token");
        String adminToken = login("admin", "Admin123!").text("token");
        assertEquals(200, request("GET", "/api/user/me", userToken, null).status());
        assertEquals(403, request("GET", "/api/admin/users", userToken, null).status());
        assertEquals(200, request("GET", "/api/admin/users", adminToken, null).status());
        String[] u = userToken.split("\\.");
        String[] a = adminToken.split("\\.");
        assertEquals(401, request("GET", "/api/admin/users", u[0]+"."+a[1]+"."+u[2], null).status());
        assertEquals(409, request("POST", "/api/auth/register", null,
                Map.of("username", "admin", "password", "Student123!")).status());
        assertEquals(400, request("POST", "/api/auth/register", null,
                Map.of("username", "short", "password", "123")).status());
        var massAssignment = request("POST", "/api/auth/register", null,
                Map.of("username", "mass_"+UUID.randomUUID(), "password", "Student123!", "roles", List.of("ROLE_ADMIN")));
        assertTrue(massAssignment.status() == 400 ||
                (massAssignment.status() == 201 && !((List<?>) massAssignment.body().get("roles")).contains("ROLE_ADMIN")));
    }

    @Test void statsAndRoleChangeApplyWithoutAnotherLogin() throws Exception {
        String name = student();
        String token = login(name, "Student123!").text("token");
        String admin = login("admin", "Admin123!").text("token");
        assertEquals(403, request("GET", "/api/admin/stats", token, null).status());
        assertEquals(401, request("GET", "/api/admin/stats", null, null).status());
        var stats = request("GET", "/api/admin/stats", admin, null);
        assertEquals(200, stats.status());
        assertEquals(users.count(), ((Number) stats.body().get("userCount")).longValue());
        long id = users.findByUsername(name).orElseThrow().getId();
        String path = "/api/admin/users/"+id+"/roles";
        assertEquals(403, request("PUT", path, token, Map.of("roles", List.of("ROLE_ADMIN"))).status());
        assertEquals(400, request("PUT", path, admin, Map.of("roles", List.of("INVALID"))).status());
        assertEquals(400, request("PUT", path, admin, Map.of("roles", List.of())).status());
        assertEquals(404, request("PUT", "/api/admin/users/999999/roles", admin,
                Map.of("roles", List.of("ROLE_ADMIN"))).status());
        assertEquals(200, request("PUT", path, admin, Map.of("roles", List.of("ROLE_ADMIN"))).status());
        assertEquals(200, request("GET", "/api/admin/stats", token, null).status());
        assertEquals(200, request("GET", "/api/user/me", token, null).status());
    }

    @Test void passwordRequiresOldPasswordAndRevokesRefreshTokens() throws Exception {
        String name = student();
        var session = login(name, "Student123!");
        String token = session.text("token");
        assertEquals(401, request("PUT", "/api/user/password", token,
                Map.of("oldPassword", "wrong", "newPassword", "Changed123!")).status());
        assertEquals(400, request("PUT", "/api/user/password", token,
                Map.of("oldPassword", "Student123!", "newPassword", "short")).status());
        assertEquals(204, request("PUT", "/api/user/password", token,
                Map.of("oldPassword", "Student123!", "newPassword", "Changed123!")).status());
        assertEquals(401, login(name, "Student123!").status());
        assertEquals(200, login(name, "Changed123!").status());
        assertTrue(users.findByUsername(name).orElseThrow().getPassword().startsWith("$2"));
        assertEquals(401, request("POST", "/api/auth/refresh", null,
                Map.of("refreshToken", session.text("refreshToken"))).status());
    }

    @Test void disablingBlocksExistingAccessAndRefreshTokensImmediately() throws Exception {
        String name = student();
        var session = login(name, "Student123!");
        String admin = login("admin", "Admin123!").text("token");
        long id = users.findByUsername(name).orElseThrow().getId();
        String path = "/api/admin/users/"+id+"/enabled";
        assertEquals(403, request("PUT", path, session.text("token"), Map.of("enabled", false)).status());
        assertEquals(200, request("PUT", path, admin, Map.of("enabled", false)).status());
        assertEquals(401, request("GET", "/api/user/me", session.text("token"), null).status());
        assertEquals(401, login(name, "Student123!").status());
        assertEquals(401, request("POST", "/api/auth/refresh", null,
                Map.of("refreshToken", session.text("refreshToken"))).status());
        assertEquals(200, request("PUT", path, admin, Map.of("enabled", true)).status());
        assertEquals(200, login(name, "Student123!").status());
        // Re-enabling must not resurrect the revoked refresh token.
        assertEquals(401, request("POST", "/api/auth/refresh", null,
                Map.of("refreshToken", session.text("refreshToken"))).status());
    }

    @Test void refreshRotatesAndExpiresAndDoesNotWorkAsAccessToken() throws Exception {
        var session = login(student(), "Student123!");
        assertEquals(900000, ((Number) session.body().get("expiresInMs")).longValue());
        assertEquals(604800000, ((Number) session.body().get("refreshExpiresInMs")).longValue());
        String raw = session.text("refreshToken");
        assertEquals(401, request("GET", "/api/user/me", raw, null).status());
        var fresh = request("POST", "/api/auth/refresh", null, Map.of("refreshToken", raw));
        assertEquals(200, fresh.status());
        assertNotEquals(raw, fresh.text("refreshToken"));
        assertEquals(200, request("GET", "/api/user/me", fresh.text("token"), null).status());
        assertEquals(401, request("POST", "/api/auth/refresh", null, Map.of("refreshToken", raw)).status());
        assertEquals(401, request("POST", "/api/auth/refresh", null,
                Map.of("refreshToken", "unknown")).status());
        // Access JWTs exceed the refresh request's size limit and fail validation.
        assertEquals(400, request("POST", "/api/auth/refresh", null,
                Map.of("refreshToken", fresh.text("token"))).status());
        // Persist expiry in the DB rather than waiting seven days.
        for (RefreshToken t : refreshTokens.findAll()) {
            if (t.getTokenHash().equals(sha256(fresh.text("refreshToken")))) {
                t.setExpiresAt(Instant.now().minusSeconds(1));
                refreshTokens.save(t);
            }
            assertNotEquals(raw, t.getTokenHash());
            assertEquals(64, t.getTokenHash().length());
        }
        assertEquals(401, request("POST", "/api/auth/refresh", null,
                Map.of("refreshToken", fresh.text("refreshToken"))).status());
    }

    @Test void concurrentRefreshCanSucceedOnlyOnce() throws Exception {
        String refresh = login(student(), "Student123!").text("refreshToken");
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            Callable<Integer> action = () -> { gate.await(); return request("POST", "/api/auth/refresh", null,
                    Map.of("refreshToken", refresh)).status(); };
            var first = pool.submit(action);
            var second = pool.submit(action);
            gate.countDown();
            assertEquals(List.of(200, 401), java.util.stream.Stream.of(first.get(), second.get()).sorted().toList());
        }
    }

    @Test @WithMockUser(roles = "USER")
    void serviceMethodSecurityRejectsUserEvenWithoutHttp() {
        assertThrows(AccessDeniedException.class, () -> management.count());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void serviceMethodSecurityAllowsAdminWithoutHttp() {
        assertTrue(management.count() >= 2);
    }

    private String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
