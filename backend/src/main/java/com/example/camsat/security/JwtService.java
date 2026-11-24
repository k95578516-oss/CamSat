package com.example.camsat.security;

import com.example.camsat.config.AppProperties;
import com.example.camsat.domain.Role;
import com.example.camsat.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(AppProperties props) {
        AppProperties.Jwt jwt = props.jwt();
        this.expirationSeconds = jwt.expirationSeconds() > 0 ? jwt.expirationSeconds() : 3600;
        String secret = jwt.secret();
        if (secret == null || secret.isBlank()) {
            if (!jwt.allowEphemeralSecret()) {
                throw new IllegalStateException("JWT_SECRET is required (at least " + MIN_SECRET_BYTES + " characters).");
            }
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            log.warn("JWT_SECRET is not set: using a random per-process secret (development only). Tokens become invalid on restart.");
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("JWT_SECRET is invalid: it must be at least " + MIN_SECRET_BYTES + " characters long.");
            }
            this.key = Keys.hmacShaKeyFor(bytes);
        }
    }

    public long expirationSeconds() { return expirationSeconds; }

    public String issue(User user) { return issue(user, Instant.now()); }

    String issue(User user, Instant now) {
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Verifies signature and expiry. Throws JwtException (ExpiredJwtException for an expired token) otherwise. */
    public AuthUser verify(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        try {
            return new AuthUser(Long.parseLong(c.getSubject()), c.get("email", String.class), Role.valueOf(c.get("role", String.class)));
        } catch (RuntimeException e) {
            throw new JwtException("Malformed token claims");
        }
    }
}