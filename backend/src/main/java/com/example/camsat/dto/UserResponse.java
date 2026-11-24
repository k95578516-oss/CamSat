package com.example.camsat.dto;

import java.time.Instant;

public record UserResponse(long id, String name, String email, String role, Instant createdAt) { }
