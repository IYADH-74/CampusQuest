package com.campusquest.auth.token;

import com.campusquest.common.InsertOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One link in a refresh-token rotation chain ("family"). Only the SHA-256 of the token is stored.
 * Rotation revokes this row and points {@code replacedBy} at its successor.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends InsertOnlyEntity {

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID familyId;

    @Column(nullable = false, updatable = false, length = 128)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant familyExpiresAt;

    private Instant revokedAt;

    private UUID replacedBy;

    @Column(length = 255, updatable = false)
    private String userAgent;

    @Column(length = 64, updatable = false)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant createdAt, Instant expiresAt,
            Instant familyExpiresAt, String userAgent, String ipAddress) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.familyExpiresAt = familyExpiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    boolean isRevoked() {
        return revokedAt != null;
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(familyExpiresAt);
    }

    void markRotated(Instant now, UUID successor) {
        this.revokedAt = now;
        this.replacedBy = successor;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getFamilyExpiresAt() {
        return familyExpiresAt;
    }
}
