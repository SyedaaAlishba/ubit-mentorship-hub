package com.ubit.mentorshiphub.auth.dto;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.ubit.mentorshiphub.user.model.AppRole;

/**
 * Request body for {@code POST /api/auth/onboarding}.
 *
 * <p>The client sends the role the user picked for themselves. It is treated as a
 * <em>claim</em>: the service accepts only the two self-selectable roles and puts
 * the account into {@code PENDING}, so nothing here can grant a capability.
 */
public record OnboardingRequest(@NotNull(message = "Choose CURRENT_STUDENT or ALUMNI") AppRole role) {

    /** Roles onboarding accepts. {@code SENIOR} and {@code ADMIN} are not offered. */
    public static Set<AppRole> selfSelectableRoles() {
        return Set.of(AppRole.STUDENT, AppRole.ALUMNI);
    }
}
