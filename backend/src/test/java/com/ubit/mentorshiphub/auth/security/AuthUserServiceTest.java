package com.ubit.mentorshiphub.auth.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ubit.mentorshiphub.auth.config.AuthProperties;
import com.ubit.mentorshiphub.common.exception.EmailInUseException;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sign-in resolution: what a completed Google login does to a local account.
 */
@ExtendWith(MockitoExtension.class)
class AuthUserServiceTest {

    private static final String SUB = "109876543210987654321";

    @Mock
    private UserService userService;

    private AuthUserService authUserService;

    @BeforeEach
    void setUp() {
        // Constructed explicitly: AuthProperties is a value object, not a mock,
        // and @InjectMocks would leave it null.
        authUserService = new AuthUserService(userService, new AuthProperties(null));
    }

    @Test
    @DisplayName("a first-time Google identity creates an account with no role and no verification")
    void firstTimeSignInCreatesUnverifiedAccount() {
        when(userService.findByGoogleId(SUB)).thenReturn(Optional.empty());
        when(userService.findByEmail("ali@example.com")).thenReturn(Optional.empty());
        when(userService.createAccount(anyString(), anyString(), any()))
                .thenAnswer(invocation -> account(null, VerificationStatus.UNVERIFIED));

        User resolved = authUserService.resolveSignedInUser(googleLogin("ali@example.com", "Ali Raza"));

        assertThat(resolved.getRole()).as("a new account must hold no role").isNull();
        assertThat(resolved.getVerificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
        assertThat(resolved.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("identity is keyed on sub, so the same email with a new sub is not a returning user")
    void differentSubIsNotTheSameAccount() {
        when(userService.findByGoogleId(SUB)).thenReturn(Optional.empty());
        // The address is already held by an account registered under another sub.
        when(userService.findByEmail("ali@example.com"))
                .thenReturn(Optional.of(account(AppRole.STUDENT,
                        VerificationStatus.VERIFIED)));

        assertThatThrownBy(() -> authUserService.resolveSignedInUser(googleLogin("ali@example.com", "Ali")))
                .isInstanceOf(EmailInUseException.class)
                .hasMessageContaining("different Google account");

        verify(userService, never()).createAccount(any(), any(), any());
    }

    @Test
    @DisplayName("a returning user keeps the role and verification status they already had")
    void returningUserKeepsRoleAndStatus() {
        User existing = account(AppRole.SENIOR,
                VerificationStatus.VERIFIED);
        when(userService.findByGoogleId(SUB)).thenReturn(Optional.of(existing));
        when(userService.recordSignIn(any(), anyString(), anyString(), any())).thenReturn(existing);

        User resolved = authUserService.resolveSignedInUser(googleLogin("ali@example.com", "Ali Raza"));

        assertThat(resolved.getRole()).isEqualTo(AppRole.SENIOR);
        assertThat(resolved.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(userService, never()).createAccount(any(), any(), any());
    }

    @Test
    @DisplayName("any Google account is accepted - an edu domain is neither required nor special")
    void acceptsAnyGoogleDomain() {
        when(userService.findByGoogleId(SUB)).thenReturn(Optional.empty());
        when(userService.findByEmail("someone@gmail.com")).thenReturn(Optional.empty());
        // Echoes the address it was handed, so the assertion is about the email
        // that arrived from Google rather than about a fixture.
        when(userService.createAccount(anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    User created = account(null, VerificationStatus.UNVERIFIED);
                    created.setGoogleId(invocation.getArgument(0));
                    created.setEmail(invocation.getArgument(1));
                    return created;
                });

        User resolved = authUserService.resolveSignedInUser(googleLogin("someone@gmail.com", "Someone"));

        assertThat(resolved.getEmail()).isEqualTo("someone@gmail.com");
    }

    @Test
    @DisplayName("the configured bootstrap address becomes a verified admin on first sign-in")
    void bootstrapAddressBecomesAdminOnCreation() {
        AuthUserService service = new AuthUserService(userService, new AuthProperties("  First.Admin@Example.com "));
        when(userService.findByGoogleId(SUB)).thenReturn(Optional.empty());
        // Google returns the address verbatim; matching it is case-insensitive.
        when(userService.findByEmail("First.Admin@Example.com")).thenReturn(Optional.empty());
        when(userService.createAccount(anyString(), anyString(), any()))
                .thenAnswer(invocation -> account(null, VerificationStatus.UNVERIFIED));
        when(userService.grantBootstrapAdmin(any(User.class)))
                .thenAnswer(invocation -> account(AppRole.ADMIN, VerificationStatus.VERIFIED));

        User resolved = service.resolveSignedInUser(googleLogin("First.Admin@Example.com", "First Admin"));

        assertThat(resolved.getRole()).isEqualTo(AppRole.ADMIN);
        assertThat(resolved.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(userService).grantBootstrapAdmin(any(User.class));
    }

    @Test
    @DisplayName("the bootstrap grant is never re-applied to an existing account")
    void bootstrapIsNotReappliedOnLaterSignIns() {
        AuthUserService service = new AuthUserService(userService, new AuthProperties("first.admin@example.com"));
        // An existing admin who has since been demoted to an ordinary account.
        User demoted = account(AppRole.STUDENT,
                VerificationStatus.REJECTED);
        when(userService.findByGoogleId(SUB)).thenReturn(Optional.of(demoted));
        when(userService.recordSignIn(any(), anyString(), anyString(), any())).thenReturn(demoted);

        User resolved = service.resolveSignedInUser(googleLogin("first.admin@example.com", "First Admin"));

        assertThat(resolved.getRole())
                .as("an env var must not silently undo a demotion")
                .isEqualTo(AppRole.STUDENT);
        verify(userService, never()).grantBootstrapAdmin(any(User.class));
    }

    @Test
    @DisplayName("an unset bootstrap address never matches, so nothing is promoted")
    void noBootstrapConfiguredPromotesNobody() {
        AuthUserService service = new AuthUserService(userService, new AuthProperties(null));
        when(userService.findByGoogleId(SUB)).thenReturn(Optional.empty());
        when(userService.findByEmail("ali@example.com")).thenReturn(Optional.empty());
        when(userService.createAccount(anyString(), anyString(), any()))
                .thenAnswer(invocation -> account(null, VerificationStatus.UNVERIFIED));

        User resolved = service.resolveSignedInUser(googleLogin("ali@example.com", "Ali"));

        assertThat(resolved.getRole()).isNull();
        verify(userService, never()).grantBootstrapAdmin(any(User.class));
    }

    @Test
    @DisplayName("a Google identity with verified email is accepted")
    void verifiedEmailIsAccepted() {
        when(userService.findByGoogleId(SUB)).thenReturn(Optional.empty());
        when(userService.findByEmail("ali@example.com")).thenReturn(Optional.empty());
        when(userService.createAccount(anyString(), anyString(), any()))
                .thenAnswer(invocation -> account(null, VerificationStatus.UNVERIFIED));

        User resolved = authUserService.resolveSignedInUser(googleLogin("ali@example.com", "Ali"));

        assertThat(resolved).isNotNull();
        verify(userService).createAccount(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a Google identity with unverified email is rejected")
    void unverifiedEmailIsRejected() {
        assertThatThrownBy(() -> authUserService.resolveSignedInUser(
                googleLoginWithEmailVerified("ali@example.com", "Ali", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is not verified");

        verify(userService, never()).findByEmail(anyString());
        verify(userService, never()).createAccount(any(), any(), any());
    }

    @Test
    @DisplayName("a Google identity missing email_verified claim is rejected")
    void missingEmailVerifiedIsRejected() {
        assertThatThrownBy(() -> authUserService.resolveSignedInUser(
                googleLoginWithEmailVerified("ali@example.com", "Ali", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email is not verified");

        verify(userService, never()).findByEmail(anyString());
        verify(userService, never()).createAccount(any(), any(), any());
    }

    @Test
    @DisplayName("a Google login with no subject claim is rejected")
    void refusesLoginWithoutSubject() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "ali@example.com");
        claims.put("email_verified", true);
        // Keyed on a present attribute, so the object is buildable; the point of
        // the test is that it carries no "sub" claim at all.
        OAuth2User googleUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), claims, "email");
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(googleUser, googleUser.getAuthorities(),
                "google");

        assertThatThrownBy(() -> authUserService.resolveSignedInUser(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subject");
    }

    private static OAuth2AuthenticationToken googleLogin(String email, String name) {
        return googleLoginWithEmailVerified(email, name, true);
    }

    private static OAuth2AuthenticationToken googleLoginWithEmailVerified(String email, String name,
            Boolean emailVerified) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", SUB);
        claims.put("email", email);
        claims.put("name", name);
        if (emailVerified != null) {
            claims.put("email_verified", emailVerified);
        }
        OAuth2User googleUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), claims, "sub");
        return new OAuth2AuthenticationToken(googleUser, googleUser.getAuthorities(), "google");
    }

    private static User account(AppRole role, VerificationStatus status) {
        User user = new User();
        user.setId(new ObjectId());
        user.setGoogleId(SUB);
        user.setEmail("ali@example.com");
        user.setName("Ali");
        user.setRole(role);
        user.setVerificationStatus(status);
        user.setEnabled(true);
        return user;
    }
}
