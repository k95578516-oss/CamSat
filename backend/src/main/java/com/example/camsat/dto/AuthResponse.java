package com.example.camsat.dto;

import java.time.Instant;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, long userId, String name,
                           String email, String role, Instant createdAt) { }
