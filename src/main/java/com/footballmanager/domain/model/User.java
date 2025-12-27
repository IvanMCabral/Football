package com.footballmanager.domain.model;

import java.time.Instant;
import java.util.Objects;

public class User {
    private final UserId id;
    private final String email;
    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private final Instant createdAt;
    private Instant updatedAt;

    public enum UserRole {
        USER, ADMIN
    }

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 50;

    private User(UserId id, String email, String username, String passwordHash, 
                UserRole role, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "UserId cannot be null");
        this.passwordHash = Objects.requireNonNull(passwordHash, "Password hash cannot be null");
        
        validateEmail(email);
        validateUsername(username);
        
        this.email = email;
        this.username = username;
        this.role = role != null ? role : UserRole.USER;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public static User create(UserId id, String email, String username, String passwordHash) {
        return new User(id, email, username, passwordHash, UserRole.USER, Instant.now(), Instant.now());
    }

    public static User createAdmin(UserId id, String email, String username, String passwordHash) {
        return new User(id, email, username, passwordHash, UserRole.ADMIN, Instant.now(), Instant.now());
    }

    public static User reconstruct(UserId id, String email, String username, String passwordHash,
                                  UserRole role, Instant createdAt, Instant updatedAt) {
        return new User(id, email, username, passwordHash, role, createdAt, updatedAt);
    }

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be empty");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (username.length() < MIN_USERNAME_LENGTH || username.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException(
                String.format("Username must be between %d and %d characters", 
                            MIN_USERNAME_LENGTH, MAX_USERNAME_LENGTH)
            );
        }
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public void updatePassword(String newPasswordHash) {
        Objects.requireNonNull(newPasswordHash, "Password hash cannot be null");
        // In a real implementation, we'd create a new immutable User object
        // For simplicity, we're just noting this should trigger an update
        this.updatedAt = Instant.now();
    }

    // Getters
    public UserId getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("User{id=%s, username='%s', email='%s', role=%s}",
                id, username, email, role);
    }
}
