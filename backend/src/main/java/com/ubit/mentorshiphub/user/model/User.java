package com.ubit.mentorshiphub.user.model;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The platform account. This is the single source of truth for
 * <em>identity</em>, <em>role</em> and <em>verification state</em> - nothing else.
 *
 * <p>Deliberately absent, because each would be redundant, derivable or
 * unverifiable from this record (see
 * {@code docs/architecture/authentication-design.md} sections 2.3 and 2.4):
 * <ul>
 *   <li>{@code universityDomain} and {@code isUniversityEmail} - signups are open
 *       to any Google account, and the domain is never a gate.</li>
 *   <li>{@code program}, {@code currentSemester}, {@code passoutYear} - claimed
 *       during onboarding and preserved in the verification record, which is
 *       where an administrator's decision is anchored.</li>
 *   <li>{@code linkedInUrl} - supporting evidence, owned by the verification
 *       record.</li>
 *   <li>the verification document - bytes live in GridFS, and only a
 *       reference to the owning application belongs in the service layer.</li>
 * </ul>
 */
@Document(collection = "users")
public class User {

    @Id
    private ObjectId id;

    /**
     * Stable Google account identifier (the OIDC {@code sub} claim). Unique and
     * the actual identity key: a re-used email address with a different
     * {@code sub} is a different person and is never merged automatically.
     */
    @Indexed(unique = true)
    private String googleId;

    /** Google-asserted email. Unique, and used only to match pre-existing accounts. */
    @Indexed(unique = true)
    private String email;

    /** Display name from the Google profile. Shown in the UI; not an identity claim. */
    private String name;

    /**
     * {@code null} until onboarding is completed. A null role carries no
     * capability whatsoever - authorization must fail closed.
     */
    private AppRole role;

    private VerificationStatus verificationStatus;

    /** When an administrator last set {@link #verificationStatus} to a decided value. */
    private Instant verificationReviewedAt;

    /**
     * Whether the account may sign in. When false, sign-in is rejected and any
     * live sessions are destroyed - see {@code AccountAccess}.
     */
    private boolean enabled;

    private Instant createdAt;

    private Instant updatedAt;

    /** Last successful Google sign-in. */
    private Instant lastLoginAt;

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getGoogleId() {
        return googleId;
    }

    public void setGoogleId(String googleId) {
        this.googleId = googleId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AppRole getRole() {
        return role;
    }

    public void setRole(AppRole role) {
        this.role = role;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Instant getVerificationReviewedAt() {
        return verificationReviewedAt;
    }

    public void setVerificationReviewedAt(Instant verificationReviewedAt) {
        this.verificationReviewedAt = verificationReviewedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * Whether this account has completed onboarding and therefore holds a role.
     * A null role means read/onboarding-only, so every capability check must
     * treat it as "no access".
     */
    public boolean hasRole() {
        return this.role != null;
    }

    /** Whether this account may take part in community interaction (Gate 1). */
    public boolean isVerified() {
        return this.verificationStatus == VerificationStatus.VERIFIED;
    }

    @Override
    public String toString() {
        return "User{id=%s, email=%s, role=%s, verificationStatus=%s, enabled=%s}"
                .formatted(id, email, role, verificationStatus, enabled);
    }
}
