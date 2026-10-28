package com.ubit.mentorshiphub.common.security;

import com.ubit.mentorshiphub.common.exception.InsufficientRoleException;
import com.ubit.mentorshiphub.common.exception.OnboardingRequiredException;
import com.ubit.mentorshiphub.common.exception.VerificationRequiredException;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reusable gate that every later feature area must go through.
 *
 * <p>The point of these tests is that role and verification status are enforced
 * as two independent dimensions: no combination of "has a role" alone is ever
 * enough to take part, and verification alone never substitutes for a role.
 */
class AuthorizationGatesTest {

    @Nested
    @DisplayName("Gate 1: onboarding")
    class Gate1Onboarding {

        @Test
        @DisplayName("an account with no role is not onboarded and gains nothing")
        void noRoleIsNotOnboarded() {
            User user = user(null, VerificationStatus.UNVERIFIED);

            assertThat(AuthorizationGates.hasCompletedOnboarding(user)).isFalse();
            assertThat(AuthorizationGates.isVerifiedParticipant(user)).isFalse();
            assertThatThrownBy(() -> AuthorizationGates.requireOnboardingComplete(user))
                    .isInstanceOf(OnboardingRequiredException.class);
        }

        @Test
        @DisplayName("holding a role means onboarding is complete")
        void roleMeansOnboarded() {
            assertThat(AuthorizationGates.hasCompletedOnboarding(
                    user(AppRole.STUDENT, VerificationStatus.UNVERIFIED))).isTrue();
        }
    }

    @Nested
    @DisplayName("Gate 1: verification")
    class Gate1Verification {

        @ParameterizedTest
        @EnumSource(value = VerificationStatus.class, names = {"UNVERIFIED", "PENDING", "REJECTED"})
        @DisplayName("an unverified account cannot take part, whatever its role")
        void unverifiedCannotParticipate(VerificationStatus status) {
            User user = user(AppRole.STUDENT, status);

            assertThat(AuthorizationGates.isVerifiedParticipant(user))
                    .as("%s must not be able to participate", status)
                    .isFalse();
            assertThatThrownBy(() -> AuthorizationGates.requireVerifiedParticipant(user))
                    .isInstanceOf(VerificationRequiredException.class);
        }

        @Test
        @DisplayName("a verified account with a role may take part")
        void verifiedMayParticipate() {
            assertThat(AuthorizationGates.isVerifiedParticipant(
                    user(AppRole.STUDENT, VerificationStatus.VERIFIED))).isTrue();
            assertThatCode(() -> AuthorizationGates.requireVerifiedParticipant(
                    user(AppRole.STUDENT, VerificationStatus.VERIFIED))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an admin is verified as a participant through the same rule as everyone else")
        void adminUsesTheSameRule() {
            assertThat(AuthorizationGates.isVerifiedParticipant(
                    user(AppRole.ADMIN, VerificationStatus.VERIFIED))).isTrue();
        }

        @Test
        @DisplayName("a pending or rejected account keeps read access - it is never blocked outright")
        void pendingAndRejectedAreNotBlockedFromReading() {
            // Verification gates community interaction only. Refusing these accounts
            // sign-in or read access would make the status page unreachable and
            // leave a rejected applicant with no way to resubmit.
            assertThat(AuthorizationGates.hasCompletedOnboarding(
                    user(AppRole.STUDENT, VerificationStatus.REJECTED))).isTrue();
        }
    }

    @Nested
    @DisplayName("Gate 2: role capability")
    class Gate2Role {

        @Test
        @DisplayName("a verified student cannot act as a senior")
        void verifiedStudentIsNotASenior() {
            User user = user(AppRole.STUDENT, VerificationStatus.VERIFIED);

            assertThat(AuthorizationGates.canActAs(user, AppRole.SENIOR)).isFalse();
            assertThatThrownBy(() -> AuthorizationGates.requireRoles(user, AppRole.SENIOR))
                    .isInstanceOf(InsufficientRoleException.class)
                    .hasMessageContaining("STUDENT");
        }

        @Test
        @DisplayName("a verified student can act wherever students are permitted")
        void verifiedStudentIsAStudent() {
            User user = user(AppRole.STUDENT, VerificationStatus.VERIFIED);

            assertThat(AuthorizationGates.canActAs(user, AppRole.STUDENT, AppRole.SENIOR)).isTrue();
        }

        @Test
        @DisplayName("Gate 2 is never reached for an unverified account - Gate 1 reports first")
        void gate1IsCheckedBeforeGate2() {
            User pendingStudent = user(AppRole.STUDENT, VerificationStatus.PENDING);

            // A student would pass a student-only capability check, so if Gate 1
            // were skipped this call would wrongly succeed.
            assertThatThrownBy(() -> AuthorizationGates.requireRoles(pendingStudent, AppRole.STUDENT))
                    .isInstanceOf(VerificationRequiredException.class);
        }

        @Test
        @DisplayName("an account with no role fails onboarding even when its status is verified")
        void noRoleFailsEvenWhenVerified() {
            User odd = user(null, VerificationStatus.VERIFIED);

            assertThatThrownBy(() -> AuthorizationGates.requireRoles(odd, AppRole.ADMIN))
                    .isInstanceOf(OnboardingRequiredException.class);
        }
    }

    private static User user(AppRole role, VerificationStatus status) {
        User user = new User();
        user.setId(new ObjectId());
        user.setEmail("ali@example.com");
        user.setName("Ali");
        user.setRole(role);
        user.setVerificationStatus(status);
        user.setEnabled(true);
        return user;
    }
}
