package com.elsyah.tech.javara.entity.user;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

/**
 * UserEntity is a JPA entity that represents the "users" table in the database. 
 * It contains fields for user ID, email, password, active status, and timestamps for creation, update, and deletion. 
 * The class includes annotations to define the table structure and constraints.
 * 
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
@Entity 
@Table (name = "users")
@SQLRestriction("deleted_at IS NULL")
public class UserEntity {
    /* 
    * Id is a unique identifier for each user. 
    * Length is set to 36 to accommodate UUIDs. It is marked as the primary key and cannot be null.
    */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /* Email is required and unique among users that have not been soft-deleted. */
    @Column (name = "email", nullable = false)
    private String email;

    /* Password is a required field for user authentication. It cannot be null. */
    @Column (name = "password", nullable = false)
    private String password;

    /* isActive indicates whether the user account is active. It defaults to true and cannot be null. */
    @Column (name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = true;

    @Column(name = "password_reset_required", nullable = false)
    private boolean passwordResetRequired;

    @Column(name = "password_reset_support_required", nullable = false)
    private boolean passwordResetSupportRequired;

    @ElementCollection
    @CollectionTable(
            name = "user_compromised_password_hashes",
            joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "password_hash", nullable = false, length = 255)
    private List<String> compromisedPasswordHashes = new ArrayList<>();

    @Column(name = "pending_email")
    private String pendingEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role = UserRole.USER;

    /* createdAt is automatically set to the current timestamp when the user is created. It cannot be null and cannot be updated. */
    @CreationTimestamp 
    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /* updatedAt is automatically set to the current timestamp whenever the user entity is updated. */
    @Column (name = "updated_at")
    private Instant updatedAt;

    /* deletedAt is used to implement soft deletion. If this field is set, the user is considered deleted. */
    @Column (name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /* Getters and setters for the fields. These methods allow access to the private fields of the class. */
    public UUID getId(){ return id; }
    public void setId(UUID id) { this.id = id; }

    /* Getters and setters for the fields. These methods allow access to the private fields of the class. */
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    /* Getters and setters for the fields. These methods allow access to the private fields of the class. */
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    /* Getters and setters for the fields. These methods allow access to the private fields of the class. */
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public boolean isPasswordResetRequired() { return passwordResetRequired; }
    public void setPasswordResetRequired(boolean passwordResetRequired) {
        this.passwordResetRequired = passwordResetRequired;
    }

    public boolean isPasswordResetSupportRequired() { return passwordResetSupportRequired; }
    public void setPasswordResetSupportRequired(boolean passwordResetSupportRequired) {
        this.passwordResetSupportRequired = passwordResetSupportRequired;
    }

    public List<String> getCompromisedPasswordHashes() { return compromisedPasswordHashes; }

    public String getPendingEmail() { return pendingEmail; }
    public void setPendingEmail(String pendingEmail) { this.pendingEmail = pendingEmail; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    /* Getters and setters for the fields. These methods allow access to the private fields of the class. */
    public Instant getCreatedAt() { return createdAt; }

    /* Getters and setters for the fields. These methods allow access to the private fields of the class. */
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    /* Getters and setters for the fields. These methods allow access to the private fields of the class. */
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
