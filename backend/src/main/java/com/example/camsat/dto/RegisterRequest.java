package com.example.camsat.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name cannot exceed 100 characters") String name,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") @Size(max = 255, message = "Email cannot exceed 255 characters") String email,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) { }
