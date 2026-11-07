package com.squared.locus.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "google_sub",
            nullable = false, unique = true)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at",
            nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(
            String googleSub,
            String email,
            String displayName) {

        this.id = UUID.randomUUID();
        this.googleSub = googleSub;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateProfile(
            String email, String displayName) {
        this.email = email;
        this.displayName = displayName;
    }
}
