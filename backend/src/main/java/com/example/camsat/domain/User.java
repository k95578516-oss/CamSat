package com.example.camsat.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, length = 255, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() { }
    public User(String name, String email, String passwordHash, Role role, Instant createdAt) {
        this.name = name; this.email = email; this.passwordHash = passwordHash; this.role = role; this.createdAt = createdAt;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
