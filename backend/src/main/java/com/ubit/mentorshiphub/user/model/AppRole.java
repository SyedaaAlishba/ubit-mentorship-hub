package com.ubit.mentorshiphub.user.model;

/**
 * Authorization role held by an account.
 *
 * <p>A role is a <em>claim</em> about the person, not a confirmation of it. It
 * never encodes verification state, and there is deliberately no composite
 * value such as {@code VERIFIED_ALUMNI} - see
 * {@code docs/architecture/authentication-design.md} section 2.2.
 *
 * <p>{@code null} role means the account has completed Google authentication
 * but not onboarding, and therefore holds no role capability at all.
 */
public enum AppRole {

    /** Currently enrolled student. Set at onboarding, confirmed by an admin. */
    STUDENT,

    /**
     * Upperclassman who can act as a mentor. Administrator-controlled only:
     * never selectable at onboarding, never inferred from semester or program.
     */
    SENIOR,

    /** Graduate who can act as a mentor and appears in the alumni directory. */
    ALUMNI,

    /** Platform operator. Bootstrap-only at first, then administrator-managed. */
    ADMIN
}
