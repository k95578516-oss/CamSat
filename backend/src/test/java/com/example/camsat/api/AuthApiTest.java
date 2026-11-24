package com.example.camsat.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.camsat.support.ApiTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends ApiTestBase {
    private static final String SECRET = "test-only-secret-test-only-secret-0123456789";

    private String body(String email, String password) throws Exception {
        return om.writeValueAsString(Map.of("name", "Ada", "email", email, "password", password));
    }

    @Test
    void registerReturnsAuthResponseWithoutPasswordHash() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("ada@example.com", "password123")))
                .andExpect(status().isCreated()).andReturn();
        JsonNode n = json(r);
        assertThat(n.fieldNames()).toIterable().containsExactlyInAnyOrder("token", "tokenType", "expiresInSeconds", "userId", "name", "email", "role", "createdAt");
        assertThat(n.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(n.get("expiresInSeconds").asLong()).isEqualTo(3600);
        assertThat(n.get("role").asText()).isEqualTo("USER");
        assertThat(r.getResponse().getContentAsString()).doesNotContain("password");
    }

    @Test
    void duplicateEmailIsConflictEvenWithDifferentCase() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("dup@example.com", "password123"))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("DUP@Example.com", "password123")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409)).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void registrationValidationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("not-an-email", "short")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists()).andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void loginSucceedsAndWrongCredentialsAreRejectedUniformly() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("login@example.com", "password123"))).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("email", "LOGIN@example.com", "password", "password123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        String wrongPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("email", "login@example.com", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("email", "nobody@example.com", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("email", "nobody@example.com", "password", "x")))).andReturn()).get("message").asText())
                .isEqualTo(om.readTree(wrongPassword).get("message").asText()); // same message: no account enumeration
        assertThat(unknownUser).contains("Invalid email or password");
    }

    @Test
    void protectedRoutesRequireAToken() throws Exception {
        for (String url : new String[]{"/api/missions", "/api/dashboard", "/api/analytics", "/api/runs/recent", "/api/runs/1", "/api/auth/me"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
    }

    @Test
    void garbageAndTamperedTokensAreRejected() throws Exception {
        Session s = register();
        mvc.perform(get("/api/missions").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized());
        String tampered = s.token().substring(0, s.token().length() - 3) + (s.token().endsWith("AAA") ? "BBB" : "AAA");
        mvc.perform(get("/api/missions").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenGetsA401WithAnExpiryMessage() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().subject("1").claim("email", "a@b.c").claim("role", "USER")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200))).expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256).compact();
        mvc.perform(get("/api/missions").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")));
    }

    @Test
    void tokenSignedWithAnotherKeyAndUnsignedTokenAreRejected() throws Exception {
        SecretKey other = Keys.hmacShaKeyFor("a-completely-different-secret-value-0123456789".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder().subject("1").claim("email", "a@b.c").claim("role", "ADMIN")
                .expiration(Date.from(Instant.now().plusSeconds(3600))).signWith(other, Jwts.SIG.HS256).compact();
        mvc.perform(get("/api/missions").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        String unsigned = Jwts.builder().subject("1").claim("role", "ADMIN").expiration(Date.from(Instant.now().plusSeconds(3600))).compact();
        mvc.perform(get("/api/missions").header("Authorization", "Bearer " + unsigned)).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheCurrentUser() throws Exception {
        Session s = register();
        mvc.perform(auth(get("/api/auth/me"), s)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(s.email())).andExpect(jsonPath("$.id").value(s.userId()));
    }

    @Test
    void healthIsPublicAndMinimal() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void corsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/missions").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/missions").header("Origin", "http://evil.example").header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
