package com.elsyah.tech.javara.entity.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "javara_refresh_tokens")
public class RefreshTokenEntity {
    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private UserSessionEntity session;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public UserSessionEntity getSession() { return session; }
    public void setSession(UserSessionEntity session) { this.session = session; }

    public UUID getUserId() { return session.getUserId(); }
    public UUID getSessionId() { return session.getId(); }
    public Instant getSessionStartedAt() { return session.getStartedAt(); }
    public Instant getLastActivityAt() { return session.getLastActivityAt(); }
    public Instant getSessionExpiresAt() { return session.getExpiresAt(); }
    public String getAuthenticationMethods() { return session.getAuthenticationMethods(); }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
}

