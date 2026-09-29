package com.jobmatching.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * NOT part of the original Python schema (jobspy/database.py). app.py keeps the pending
 * registration (including the verification code) in the Flask session cookie, which is
 * signed but not encrypted — the code is readable client-side. With stateless JWTs there
 * is no session, so the pending sign-up lives server-side here instead, which also lets
 * us cap wrong-code attempts. Like PasswordResetToken, this table must be created once
 * before startup (ddl-auto=validate):
 *
 * <pre>
 * CREATE TABLE pending_registrations (
 *     id            UUID PRIMARY KEY,
 *     email         VARCHAR(500) NOT NULL UNIQUE,
 *     username      VARCHAR(200) NOT NULL,
 *     first_name    VARCHAR(200),
 *     last_name     VARCHAR(200),
 *     password_hash VARCHAR(500) NOT NULL,
 *     code          VARCHAR(6)   NOT NULL,
 *     attempts      INTEGER      NOT NULL DEFAULT 0,
 *     expires_at    TIMESTAMP    NOT NULL,
 *     created_at    TIMESTAMP
 * );
 * </pre>
 */
@Entity
@Table(name = "pending_registrations")
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "email", length = 500, nullable = false, unique = true)
    private String email;

    @Column(name = "username", length = 200, nullable = false)
    private String username;

    @Column(name = "first_name", length = 200)
    private String firstName;

    @Column(name = "last_name", length = 200)
    private String lastName;

    @Column(name = "password_hash", length = 500, nullable = false)
    private String passwordHash;

    @Column(name = "code", length = 6, nullable = false)
    private String code;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
