package com.example.camsat.service;

import com.example.camsat.domain.Role;
import com.example.camsat.domain.User;
import com.example.camsat.dto.AuthResponse;
import com.example.camsat.dto.LoginRequest;
import com.example.camsat.dto.RegisterRequest;
import com.example.camsat.dto.UserResponse;
import com.example.camsat.repository.UserRepository;
import com.example.camsat.security.JwtService;
import com.example.camsat.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final String INVALID_CREDENTIALS = "Invalid email or password.";
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final Clock clock;
    /** Used to spend the same hashing time when the email is unknown, so timing does not reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, Clock clock) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.clock = clock;
        this.dummyHash = encoder.encode("camsat-timing-equaliser");
    }

    static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    public AuthResponse register(RegisterRequest req) {
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password must be at most 72 bytes.");
        }
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) throw ApiException.conflict("An account with this email already exists.");
        try {
            User saved = users.saveAndFlush(new User(req.name().trim(), email, encoder.encode(req.password()), Role.USER, clock.instant()));
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) { // lost a race with a concurrent registration
            throw ApiException.conflict("An account with this email already exists.");
        }
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(normalizeEmail(req.email())).orElse(null);
        if (user == null) {
            encoder.matches(req.password(), dummyHash);
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) throw ApiException.unauthorized(INVALID_CREDENTIALS);
        return toResponse(user);
    }

    public UserResponse me(long userId) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Authentication is required."));
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getCreatedAt());
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwt.issue(u), "Bearer", jwt.expirationSeconds(), u.getId(), u.getName(), u.getEmail(),
                u.getRole().name(), u.getCreatedAt());
    }
}
