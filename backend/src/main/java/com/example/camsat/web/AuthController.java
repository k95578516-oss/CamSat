package com.example.camsat.web;

import com.example.camsat.dto.AuthResponse;
import com.example.camsat.dto.LoginRequest;
import com.example.camsat.dto.RegisterRequest;
import com.example.camsat.dto.UserResponse;
import com.example.camsat.security.AuthUser;
import com.example.camsat.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) { return auth.register(req); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) { return auth.login(req); }

    /** Additive endpoint; not used by the current frontend. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser user) { return auth.me(user.id()); }
}
