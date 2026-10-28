package com.ubit.mentorshiphub.auth.dto;

import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The five auth states the frontend routes on.
 */
class CurrentUserResponseTest {

    @Test
    @DisplayName("an anonymous caller gets NOT_AUTHENTICATED and no user details")
    void anonymousHasNoIdentity() {
        CurrentUserResponse response = CurrentUserResponse.notAuthenticated();

        assertThat(response.state()).isEqualTo(AuthState.NOT_AUTHENTICATED);
        assertThat(response.userId()).isNull();
        assertThat(response.email()).isNull();
        assertThat(response.role()).isNull();
    }

    @Test
    @DisplayName("a signed-in account with no role reads as ONBOARDING_REQUIRED")
    void noRoleIsOnboardingRequired() {
        CurrentUserResponse response = CurrentUserResponse.of(user(null, VerificationStatus.UNVERIFIED));

        assertThat(response.state()).isEqualTo(AuthState.ONBOARDING_REQUIRED);
        assertThat(response.onboardingComplete()).isFalse();
    }

    @Test
    @DisplayName("a role with no application yet reads as VERIFICATION_REQUIRED")
    void roleWithoutApplicationIsVerificationRequired() {
        CurrentUserResponse response = CurrentUserResponse.of(user(AppRole.STUDENT, VerificationStatus.UNVERIFIED));

        assertThat(response.state()).isEqualTo(AuthState.VERIFICATION_REQUIRED);
        assertThat(response.role()).isEqualTo(AppRole.STUDENT);
        assertThat(response.onboardingComplete()).isTrue();
        assertThat(response.canSubmitVerification())
                .as("UNVERIFIED can still make a first submission")
                .isTrue();
    }

    @Test
    @DisplayName("an alumni role with no application yet reads as VERIFICATION_REQUIRED")
    void alumniRoleWithoutApplicationIsVerificationRequired() {
        CurrentUserResponse response = CurrentUserResponse.of(user(AppRole.ALUMNI, VerificationStatus.UNVERIFIED));

        assertThat(response.state()).isEqualTo(AuthState.VERIFICATION_REQUIRED);
        assertThat(response.role()).isEqualTo(AppRole.ALUMNI);
        assertThat(response.onboardingComplete()).isTrue();
        assertThat(response.canSubmitVerification()).isTrue();
    }

    @Test
    @DisplayName("a submitted application reads as PENDING_VERIFICATION and cannot be resubmitted")
    void submittedApplicationIsPending() {
        CurrentUserResponse response = CurrentUserResponse.of(user(AppRole.STUDENT, VerificationStatus.PENDING));

        assertThat(response.state()).isEqualTo(AuthState.PENDING_VERIFICATION);
        assertThat(response.canSubmitVerification())
                .as("a pending application must not be replaceable")
                .isFalse();
    }

    @Test
    @DisplayName("a declined application reads as REJECTED and may be resubmitted")
    void rejectedMayResubmit() {
        CurrentUserResponse response = CurrentUserResponse.of(user(AppRole.STUDENT, VerificationStatus.REJECTED));

        assertThat(response.state()).isEqualTo(AuthState.REJECTED);
        assertThat(response.canSubmitVerification()).isTrue();
        assertThat(response.role())
                .as("rejection must not take the role away")
                .isEqualTo(AppRole.STUDENT);
    }

    @Test
    @DisplayName("a verified account reads as VERIFIED")
    void verifiedIsVerified() {
        CurrentUserResponse response = CurrentUserResponse.of(user(AppRole.ALUMNI, VerificationStatus.VERIFIED));

        assertThat(response.state()).isEqualTo(AuthState.VERIFIED);
        assertThat(response.canSubmitVerification()).isFalse();
    }

    @Test
    @DisplayName("a verified status with no role still reads as ONBOARDING_REQUIRED")
    void verifiedStatusWithoutRoleIsNotReady() {
        // A null role grants nothing, so the account must not be told it is ready
        // to use the platform just because the status happens to be VERIFIED.
        CurrentUserResponse response = CurrentUserResponse.of(user(null, VerificationStatus.VERIFIED));

        assertThat(response.state()).isEqualTo(AuthState.ONBOARDING_REQUIRED);
    }

    @Test
    @DisplayName("the user id is exposed as a hex string, not a BSON object")
    void userIdIsHexString() {
        ObjectId id = new ObjectId();
        User user = user(AppRole.STUDENT, VerificationStatus.PENDING);
        user.setId(id);

        assertThat(CurrentUserResponse.of(user).userId()).isEqualTo(id.toHexString());
    }

    private static User user(AppRole role, VerificationStatus status) {
        User user = new User();
        user.setId(new ObjectId());
        user.setEmail("ali@example.com");
        user.setName("Ali Raza");
        user.setRole(role);
        user.setVerificationStatus(status);
        user.setEnabled(true);
        return user;
    }
}
