package com.example.camsat.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank(message = "Email is required") @Size(max = 255) String email,
        @NotBlank(message = "Password is required") @Size(max = 72) String password) { }
