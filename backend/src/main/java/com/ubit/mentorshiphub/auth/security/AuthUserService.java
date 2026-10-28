package com.ubit.mentorshiphub.auth.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.ubit.mentorshiphub.auth.config.AuthProperties;
import com.ubit.mentorshiphub.common.exception.EmailInUseException;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.service.UserService;

/**
 * Turns a successful Google authentication into a local account.
 *
 * <p>Identity is keyed on the OIDC {@code sub} claim, which is stable for a
 * Google account forever. Email is used only to spot a pre-existing account, and
 * never to silently take one over: if an address already belongs to a different
 * {@code sub}, sign-in is refused instead of merged, because guessing there is
 * how accounts get hijacked.
 *
 * <p>Any Google account is accepted. There is no domain check, because the
 * platform is not restricted to one university and an email domain proves
 * nothing about verification status.
 */
@Service
public class AuthUserService {

    private final UserService userService;
    private final AuthProperties authProperties;

    public AuthUserService(UserService userService, AuthProperties authProperties) {
        this.userService = userService;
        this.authProperties = authProperties;
    }

    /**
     * Finds or creates the account behind a completed Google login and records the
     * sign-in.
     */
    public User resolveSignedInUser(OAuth2AuthenticationToken authentication) {
        OAuth2User googleUser = authentication.getPrincipal();
        String googleId = readClaim(googleUser, "sub");
        if (googleId == null || googleId.isBlank()) {
            throw new IllegalStateException("Google did not return a subject identifier.");
        }
        String email = readClaim(googleUser, "email");
        String name = readClaim(googleUser, "name");

        // Enforce email verification per approved authentication design.
        // Decision must be based on Google/OIDC identity only.
        Object emailVerifiedClaim = googleUser.getAttribute("email_verified");
        if (!isEmailVerified(emailVerifiedClaim)) {
            throw new IllegalStateException("Google account email is not verified.");
        }

        Optional<User> existing = userService.findByGoogleId(googleId);
        if (existing.isPresent()) {
            // Returning account. Refresh the profile fields Google owns; a name
            // or email change must not alter the role or verification status.
            return userService.recordSignIn(existing.get(), googleId, email, name);
        }

        throwOnEmailOwnedByAnotherIdentity(email);

        User created = userService.createAccount(googleId, email, name);
        if (authProperties.isBootstrapAdminEmail(email)) {
            // Applied only on the creating sign-in, so removing the environment
            // variable - or demoting the account later - is not undone by the
            // next login.
            created = userService.grantBootstrapAdmin(created);
        }
        return created;
    }

    /** Builds the session principal for the account behind a completed login. */
    public AuthenticatedUser toPrincipal(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            return principal;
        }
        throw new IllegalStateException("Authentication does not carry an application principal.");
    }

    private void throwOnEmailOwnedByAnotherIdentity(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        userService.findByEmail(email).ifPresent(existing -> {
            throw new EmailInUseException(
                    "This email address is already registered to a different Google account. "
                            + "Sign in with the original Google account, or contact an administrator to merge them.");
        });
    }

    private static String readClaim(OAuth2User googleUser, String claim) {
        Object value = googleUser.getAttribute(claim);
        return value == null ? null : value.toString();
    }

    private static boolean isEmailVerified(Object emailVerifiedClaim) {
        if (emailVerifiedClaim == null) {
            return false;
        }
        if (emailVerifiedClaim instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (emailVerifiedClaim instanceof String stringValue) {
            return "true".equalsIgnoreCase(stringValue.trim());
        }
        try {
            return Boolean.parseBoolean(emailVerifiedClaim.toString());
        } catch (Exception ex) {
            return false;
        }
    }
}
