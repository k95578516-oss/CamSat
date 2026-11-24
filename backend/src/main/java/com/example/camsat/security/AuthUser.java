package com.example.camsat.security;

import com.example.camsat.domain.Role;

/** The authenticated principal. Always derived from a verified JWT, never from request data. */
public record AuthUser(long id, String email, Role role) { }
