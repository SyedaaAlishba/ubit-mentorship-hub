package com.ubit.mentorshiphub.auth.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.ubit.mentorshiphub.user.model.AppRole;

/**
 * Derives Spring Security authorities from the {@link AuthenticatedUser}
 * principal: {@code ROLE_STUDENT}, {@code ROLE_ALUMNI}, {@code ROLE_SENIOR},
 * {@code ROLE_ADMIN}.
 *
 * <p>The null branch is the point of this class: an account that has
 * authenticated but not onboarded receives <em>no</em> authority, so
 * authorization fails closed by construction rather than by each caller
 * remembering to check.
 *
 * <p>Only the role becomes an authority. Verification status is deliberately
 * not promoted to one: it is an independently-managed claim state, and hiding
 * it in the authority list would make the two look interchangeable. It is
 * enforced as Gate 1 in {@code AuthorizationGates}, against account state that
 * is re-read per request.
 */
public final class UserRoleGrantedAuthoritiesMapper {

    public static final String ROLE_PREFIX = "ROLE_";

    private UserRoleGrantedAuthoritiesMapper() {
    }

    public static Collection<GrantedAuthority> toAuthorities(AuthenticatedUser principal) {
        if (principal == null || principal.role() == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority(ROLE_PREFIX + principal.role().name()));
    }

    /** True when the account holds the given role, for {@code hasRole(...)} expressions. */
    public static boolean hasRole(AuthenticatedUser principal, AppRole expected) {
        return principal != null && principal.hasRole(expected);
    }
}
