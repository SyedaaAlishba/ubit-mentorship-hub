package com.ubit.mentorshiphub.auth.security;

import java.io.Serializable;

import org.bson.types.ObjectId;

import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;

/**
 * The tiny immutable principal stored in the server-side session, replacing the
 * raw {@code OidcUser} so that no Google access or refresh token is ever kept
 * server-side.
 *
 * <p>Serializable because Spring Session persists sessions to MongoDB.
 *
 * <p>Holds only the account id, the email shown in the UI, and a role snapshot
 * used for coarse {@code hasRole(...)} matching. It intentionally carries no
 * verification status: that changes independently of the role, and a copy in
 * the session would go stale. Every decision that matters reads the account
 * again through {@code AccountAccess}.
 */
public record AuthenticatedUser(
        ObjectId userId,
        String email,
        AppRole role
) implements Serializable {

    /** Builds the principal for an account, after it has been persisted. */
    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
    }

    /** Whether onboarding has been completed, i.e. whether a role is held. */
    public boolean hasRole() {
        return this.role != null;
    }

    /** Whether this principal holds the given role. Never true for a null role. */
    public boolean hasRole(AppRole expected) {
        return this.role != null && this.role == expected;
    }
}
