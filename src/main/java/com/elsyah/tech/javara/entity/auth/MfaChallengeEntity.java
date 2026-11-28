package com.elsyah.tech.javara.entity.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "javara_mfa_challenges")
public class MfaChallengeEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private MfaChallengePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 24)
    private MfaMethod method;

    @Column(name = "code_verifier", length = 64)
    private String codeVerifier;

    @Column(name = "password_hash_digest", nullable = false, length = 64)
    private String passwordHashDigest;

    @Column(name = "options_json", columnDefinition = "TEXT")
    private String optionsJson;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public MfaChallengePurpose getPurpose() { return purpose; }
    public void setPurpose(MfaChallengePurpose purpose) { this.purpose = purpose; }

    public MfaMethod getMethod() { return method; }
    public void setMethod(MfaMethod method) { this.method = method; }

    public String getCodeVerifier() { return codeVerifier; }
    public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }

    public String getPasswordHashDigest() { return passwordHashDigest; }
    public void setPasswordHashDigest(String passwordHashDigest) {
        this.passwordHashDigest = passwordHashDigest;
    }

    public String getOptionsJson() { return optionsJson; }
    public void setOptionsJson(String optionsJson) { this.optionsJson = optionsJson; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
