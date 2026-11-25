package com.campusquest.auth.token;

import com.campusquest.common.InsertOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A single-use secret (email code or link token). Only a hash is stored, never the secret itself. */
@Entity
@Table(name = "one_time_tokens")
public class OneTimeToken extends InsertOnlyEntity {

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private TokenPurpose purpose;

    @Column(nullable = false, length = 128)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected OneTimeToken() {}

    public OneTimeToken(UUID userId, TokenPurpose purpose, Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public boolean isUsable(Instant now, int maxAttempts) {
        return consumedAt == null && now.isBefore(expiresAt) && attempts < maxAttempts;
    }

    void recordFailedAttempt() {
        attempts++;
    }

    void consume(Instant now) {
        consumedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public TokenPurpose getPurpose() {
        return purpose;
    }

    public String getTokenHash() {
        return tokenHash;
    }
}
