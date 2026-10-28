package com.ubit.mentorshiphub.user.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.ubit.mentorshiphub.common.exception.EmailInUseException;
import com.ubit.mentorshiphub.common.exception.OnboardingConflictException;
import com.ubit.mentorshiphub.common.exception.VerificationStateConflictException;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.repository.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Account lifecycle rules: what may change an account, when, and who decides.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final String GOOGLE_SUB = "109876543210987654321";
    private static final String EMAIL = "Ali.Student@Example.COM";

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private User givenSavedUser() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(new ObjectId());
            }
            return saved;
        });
        return null;
    }

    @Nested
    @DisplayName("account creation")
    class Creation {

        @Test
        @DisplayName("a new account is enabled but holds no role and is not verified")
        void newAccountIsUnverifiedWithNoRole() {
            givenSavedUser();

            User created = userService.createAccount(GOOGLE_SUB, EMAIL, "  Ali Raza  ");

            assertThat(created.getRole()).as("role must stay null until onboarding").isNull();
            assertThat(created.hasRole()).isFalse();
            assertThat(created.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
            assertThat(created.isEnabled()).isTrue();
            assertThat(created.getCreatedAt()).isEqualTo(NOW);
            assertThat(created.getLastLoginAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("the email is normalised and the name trimmed")
        void normalisesEmailAndName() {
            givenSavedUser();

            User created = userService.createAccount(GOOGLE_SUB, EMAIL, "  Ali Raza  ");

            assertThat(created.getEmail()).isEqualTo("ali.student@example.com");
            assertThat(created.getName()).isEqualTo("Ali Raza");
        }

        @Test
        @DisplayName("a missing name falls back rather than storing null")
        void fallsBackWhenNameMissing() {
            givenSavedUser();

            User created = userService.createAccount(GOOGLE_SUB, EMAIL, "   ");

            assertThat(created.getName()).isEqualTo("UBIT Member");
        }

        @Test
        @DisplayName("an account without an email is refused")
        void refusesAccountWithoutEmail() {
            assertThatThrownBy(() -> userService.createAccount(GOOGLE_SUB, "  ", "Ali"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("onboarding")
    class Onboarding {

        @Test
        @DisplayName("onboarding records the claim but leaves it unverified until a submission")
        void acceptsStudentClaim() {
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User result = userService.completeOnboarding(givenUnverifiedUser(), AppRole.STUDENT);

            assertThat(result.getRole()).isEqualTo(AppRole.STUDENT);
            assertThat(result.hasRole()).isTrue();
            assertThat(result.getVerificationStatus())
                    .as("onboarding claims identity; only a submission starts verification")
                    .isEqualTo(VerificationStatus.UNVERIFIED);
        }

        @Test
        @DisplayName("SENIOR cannot be claimed at onboarding")
        void refusesSeniorRole() {
            assertThatThrownBy(() -> userService.completeOnboarding(givenUnverifiedUser(), AppRole.SENIOR))
                    .isInstanceOf(OnboardingConflictException.class)
                    .hasMessageContaining("CURRENT_STUDENT or ALUMNI");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("ADMIN cannot be claimed at onboarding")
        void refusesAdminRole() {
            assertThatThrownBy(() -> userService.completeOnboarding(givenUnverifiedUser(), AppRole.ADMIN))
                    .isInstanceOf(OnboardingConflictException.class);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("onboarding is idempotent when submitting the same role for an unverified user")
        void allowsIdenticalRoleOnboardingWhenUnverified() {
            User already = givenUser(AppRole.STUDENT, VerificationStatus.UNVERIFIED);

            User result = userService.completeOnboarding(already, AppRole.STUDENT);

            assertThat(result.getRole()).isEqualTo(AppRole.STUDENT);
            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        }

        @Test
        @DisplayName("onboarding cannot change role once a role is already held")
        void refusesChangingRoleAfterOnboarding() {
            User already = givenUser(AppRole.STUDENT, VerificationStatus.UNVERIFIED);

            assertThatThrownBy(() -> userService.completeOnboarding(already, AppRole.ALUMNI))
                    .isInstanceOf(OnboardingConflictException.class)
                    .hasMessageContaining("already been completed");
        }

        @Test
        @DisplayName("onboarding cannot be repeated once application is in progress or completed")
        void refusesSecondOnboarding() {
            User already = givenUser(AppRole.STUDENT, VerificationStatus.PENDING);

            assertThatThrownBy(() -> userService.completeOnboarding(already, AppRole.STUDENT))
                    .isInstanceOf(OnboardingConflictException.class)
                    .hasMessageContaining("already been completed");
        }
    }

    @Nested
    @DisplayName("verification state machine")
    class Verification {

        @Test
        @DisplayName("UNVERIFIED -> PENDING on first submission")
        void startsVerification() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.UNVERIFIED);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User result = userService.startVerification(user);

            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
            assertThat(result.getVerificationReviewedAt()).isNull();
        }

        @Test
        @DisplayName("REJECTED -> PENDING on resubmission, keeping the role")
        void resubmissionKeepsRole() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.REJECTED);
            user.setVerificationReviewedAt(NOW.minusSeconds(3600));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User result = userService.startVerification(user);

            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
            assertThat(result.getRole()).as("rejection must not have taken the role away").isEqualTo(AppRole.STUDENT);
        }

        @Test
        @DisplayName("a pending application cannot be replaced by another submission")
        void refusesResubmissionWhilePending() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.PENDING);

            assertThatThrownBy(() -> userService.startVerification(user))
                    .isInstanceOf(VerificationStateConflictException.class);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("VERIFIED never regresses to PENDING")
        void refusesToUnverify() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.VERIFIED);

            assertThatThrownBy(() -> userService.startVerification(user))
                    .isInstanceOf(VerificationStateConflictException.class);
        }

        @Test
        @DisplayName("approval records the decision time and leaves the role alone")
        void approvesApplication() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.PENDING);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User result = userService.applyVerificationDecision(user, VerificationStatus.VERIFIED);

            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(result.getVerificationReviewedAt()).isEqualTo(NOW);
            assertThat(result.getRole()).isEqualTo(AppRole.STUDENT);
        }

        @Test
        @DisplayName("rejection preserves the role so the account can resubmit")
        void rejectionPreservesRole() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.PENDING);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User result = userService.applyVerificationDecision(user, VerificationStatus.REJECTED);

            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
            assertThat(result.getRole()).isEqualTo(AppRole.STUDENT);
            assertThat(result.getVerificationStatus().isApplicationStartable()).isTrue();
        }

        @Test
        @DisplayName("an application can only be decided while it is pending")
        void refusesDecisionOutsidePending() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.VERIFIED);

            assertThatThrownBy(() -> userService.applyVerificationDecision(user, VerificationStatus.REJECTED))
                    .isInstanceOf(VerificationStateConflictException.class);
        }

        @Test
        @DisplayName("PENDING is not a decision an administrator can apply")
        void refusesToApplyPendingAsDecision() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.PENDING);

            assertThatThrownBy(() -> userService.applyVerificationDecision(user, VerificationStatus.PENDING))
                    .isInstanceOf(VerificationStateConflictException.class);
        }
    }

    @Nested
    @DisplayName("bootstrap administrator")
    class BootstrapAdmin {

        @Test
        @DisplayName("granting the role also pre-verifies, skipping onboarding")
        void grantsAdminAndVerifies() {
            User user = givenUser(AppRole.STUDENT, VerificationStatus.UNVERIFIED);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            User result = userService.grantBootstrapAdmin(user);

            assertThat(result.getRole()).isEqualTo(AppRole.ADMIN);
            assertThat(result.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(result.getVerificationReviewedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("email ownership")
    class EmailOwnership {

        @Test
        @DisplayName("an email change onto another account's address is refused")
        void refusesEmailAlreadyOwned() {
            User current = givenUser(AppRole.STUDENT, VerificationStatus.VERIFIED);
            ObjectId otherId = new ObjectId();
            when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(
                    givenUser(AppRole.ALUMNI, VerificationStatus.VERIFIED, otherId)));

            assertThatThrownBy(() -> userService.recordSignIn(current, GOOGLE_SUB, "taken@example.com", "Ali"))
                    .isInstanceOf(EmailInUseException.class);

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("keeping your own address across sign-ins is allowed")
        void allowsReusingOwnAddress() {
            User current = givenUser(AppRole.STUDENT, VerificationStatus.VERIFIED, new ObjectId());
            current.setEmail("ali@example.com");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // A case-only difference is not a new address, so no ownership lookup
            // happens at all and the sign-in is accepted.
            assertThatCode(() -> userService.recordSignIn(current, GOOGLE_SUB, "ALI@Example.com", "Ali Raza"))
                    .doesNotThrowAnyException();

            verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        }

        @Test
        @DisplayName("a returning sign-in refreshes the name and sign-in time")
        void refreshesProfileOnReturn() {
            User current = givenUser(AppRole.STUDENT, VerificationStatus.VERIFIED);
            current.setName("Old Name");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.recordSignIn(current, GOOGLE_SUB, current.getEmail(), "New Name");

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getName()).isEqualTo("New Name");
            assertThat(saved.getValue().getLastLoginAt()).isEqualTo(NOW);
            assertThat(saved.getValue().getRole()).as("sign-in must not touch the role").isEqualTo(AppRole.STUDENT);
        }
    }

    private User givenUnverifiedUser() {
        User user = new User();
        user.setId(new ObjectId());
        user.setGoogleId(GOOGLE_SUB);
        user.setEmail("ali@example.com");
        user.setRole(null);
        user.setVerificationStatus(VerificationStatus.UNVERIFIED);
        user.setEnabled(true);
        return user;
    }

    private User givenUser(AppRole role, VerificationStatus status) {
        return givenUser(role, status, new ObjectId());
    }

    private User givenUser(AppRole role, VerificationStatus status, ObjectId id) {
        User user = new User();
        user.setId(id);
        user.setGoogleId(GOOGLE_SUB);
        user.setEmail("ali@example.com");
        user.setName("Ali");
        user.setRole(role);
        user.setVerificationStatus(status);
        user.setEnabled(true);
        user.setCreatedAt(NOW);
        user.setUpdatedAt(NOW);
        return user;
    }
}
