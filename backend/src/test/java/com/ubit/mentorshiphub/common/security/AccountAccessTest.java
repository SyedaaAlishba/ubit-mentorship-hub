package com.ubit.mentorshiphub.common.security;

import com.ubit.mentorshiphub.auth.security.ApplicationAuthentication;
import com.ubit.mentorshiphub.auth.security.AuthenticatedUser;
import com.ubit.mentorshiphub.auth.security.UserRoleGrantedAuthoritiesMapper;
import com.ubit.mentorshiphub.common.exception.AccountDisabledException;
import com.ubit.mentorshiphub.common.exception.AccountNotFoundException;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import com.ubit.mentorshiphub.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Per-request account resolution, which is what makes a disabled account or a
 * revoked role take effect immediately instead of at session expiry.
 */
@ExtendWith(MockitoExtension.class)
class AccountAccessTest {

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    private AccountAccess accountAccess;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("an anonymous caller is not signed in")
    void anonymousIsNotSignedIn() {
        accountAccess = new AccountAccess(userService);

        assertThat(accountAccess.isSignedIn()).isFalse();
    }

    @Test
    @DisplayName("an enabled account is resolved from storage, not from the session snapshot")
    void resolvesFreshAccountState() {
        accountAccess = new AccountAccess(userService);
        ObjectId id = new ObjectId();
        signIn(new AuthenticatedUser(id, "ali@example.com", AppRole.SENIOR));

        // The stored role differs from the session's snapshot: the fresh value wins.
        User stored = account(id, AppRole.STUDENT, true);
        when(userService.findById(id)).thenReturn(Optional.of(stored));

        assertThat(accountAccess.requireCurrentUser().getRole())
                .as("authorization must follow current state, not the session copy")
                .isEqualTo(AppRole.STUDENT);
    }

    @Test
    @DisplayName("a disabled account is refused even though its session is still valid")
    void refusesDisabledAccount() {
        accountAccess = new AccountAccess(userService);
        ObjectId id = new ObjectId();
        signIn(new AuthenticatedUser(id, "ali@example.com", AppRole.STUDENT));
        when(userService.findById(id)).thenReturn(Optional.of(account(id, AppRole.STUDENT, false)));

        assertThatThrownBy(() -> accountAccess.requireCurrentUser())
                .isInstanceOf(AccountDisabledException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @DisplayName("a session pointing at a deleted account is refused")
    void refusesSessionForDeletedAccount() {
        accountAccess = new AccountAccess(userService);
        ObjectId id = new ObjectId();
        signIn(new AuthenticatedUser(id, "ali@example.com", AppRole.STUDENT));
        when(userService.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountAccess.requireCurrentUser())
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("the account is loaded once per request, then reused")
    void memoizesWithinOneRequest() {
        accountAccess = new AccountAccess(userService);
        ObjectId id = new ObjectId();
        signIn(new AuthenticatedUser(id, "ali@example.com", AppRole.STUDENT));
        when(userService.findById(id)).thenReturn(Optional.of(account(id, AppRole.STUDENT, true)));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.getSession(true);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        accountAccess.requireCurrentUser();
        accountAccess.requireCurrentUser();

        verify(userService).findById(id);
    }

    @Test
    @DisplayName("disabling an account invalidates its session so the cookie cannot be replayed")
    void invalidatesSessionForDisabledAccount() {
        accountAccess = new AccountAccess(userService);
        ObjectId id = new ObjectId();
        signIn(new AuthenticatedUser(id, "ali@example.com", AppRole.STUDENT));
        when(userService.findById(id)).thenReturn(Optional.of(account(id, AppRole.STUDENT, false)));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        httpRequest.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(httpRequest));

        assertThatThrownBy(() -> accountAccess.requireCurrentUser())
                .isInstanceOf(AccountDisabledException.class);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("an account with no role gets no authority at all")
    void noRoleMeansNoAuthority() {
        AuthenticatedUser principal = new AuthenticatedUser(new ObjectId(), "ali@example.com", null);

        assertThat(UserRoleGrantedAuthoritiesMapper.toAuthorities(principal)).isEmpty();
    }

    @Test
    @DisplayName("a role yields exactly one ROLE_ authority")
    void roleYieldsSingleAuthority() {
        AuthenticatedUser principal = new AuthenticatedUser(new ObjectId(), "ali@example.com", AppRole.ADMIN);

        assertThat(UserRoleGrantedAuthoritiesMapper.toAuthorities(principal))
                .extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("verification status is never turned into an authority")
    void verificationStatusIsNotAnAuthority() {
        // Authorities come from the role alone, so a status change cannot require
        // a re-login to take effect and the two cannot be conflated.
        assertThat(UserRoleGrantedAuthoritiesMapper.toAuthorities(
                new AuthenticatedUser(new ObjectId(), "ali@example.com", AppRole.STUDENT)))
                .extracting(Object::toString)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    @DisplayName("the principal carries no credentials, so no Google token is persisted")
    void principalCarriesNoCredentials() {
        AuthenticatedUser principal = new AuthenticatedUser(new ObjectId(), "ali@example.com", AppRole.STUDENT);
        ApplicationAuthentication authentication = new ApplicationAuthentication(
                principal, UserRoleGrantedAuthoritiesMapper.toAuthorities(principal));

        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isSameAs(principal);
        assertThat(ApplicationAuthentication.principalOf(authentication)).isEqualTo(principal);
    }

    @Test
    @DisplayName("signing in with no principal at all does not blow up")
    void toleratesMissingPrincipal() {
        accountAccess = new AccountAccess(userService);

        assertThatCode(() -> assertThat(accountAccess.isSignedIn()).isFalse()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the in-flight Google handshake is not treated as a signed-in session")
    void ignoresHandshakeToken() {
        accountAccess = new AccountAccess(userService);
        SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                        new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                                List.of(), java.util.Map.of("sub", "1"), "sub"),
                        List.of(), "google"));

        assertThat(accountAccess.isSignedIn()).isFalse();
    }

    private void signIn(AuthenticatedUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new ApplicationAuthentication(principal, UserRoleGrantedAuthoritiesMapper.toAuthorities(principal)));
    }

    private static User account(ObjectId id, AppRole role, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setEmail("ali@example.com");
        user.setName("Ali");
        user.setRole(role);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setEnabled(enabled);
        return user;
    }
}
