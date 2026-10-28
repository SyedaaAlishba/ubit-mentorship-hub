package com.ubit.mentorshiphub.auth.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * The authentication stored in the session once Google sign-in has completed.
 *
 * <p>Replaces the {@code OAuth2AuthenticationToken} so that the session holds a
 * small application principal instead of Google's {@code OidcUser}. That matters
 * for two reasons: no Google access or refresh token is persisted server-side,
 * and authorities can be derived from our own role rather than from Google's
 * scopes.
 *
 * <p>Credentials are intentionally {@code null} - there is nothing secret to
 * replay, and re-authenticating is what sign-in is for.
 */
public class ApplicationAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    /**
     * Not transient. MongoDB-backed sessions serialize this object, and a
     * transient field would be restored as null - leaving a signed-in session
     * that serializes and then fails every subsequent request. {@link
     * AuthenticatedUser} is an immutable record of three ids and strings, safe to
     * write to the session store.
     */
    private final AuthenticatedUser principal;

    public ApplicationAuthentication(AuthenticatedUser principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    /** The session id for this authentication, used by the session registry. */
    public String accountEmail() {
        return this.principal.email();
    }

    @Override
    public String getName() {
        return this.principal.email();
    }

    /** Convenience for callers that hold the base {@link Authentication} type. */
    public static AuthenticatedUser principalOf(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? user
                : null;
    }
}
