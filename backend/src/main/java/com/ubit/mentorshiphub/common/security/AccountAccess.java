package com.ubit.mentorshiphub.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ubit.mentorshiphub.auth.security.AuthenticatedUser;
import com.ubit.mentorshiphub.common.exception.AccountDisabledException;
import com.ubit.mentorshiphub.common.exception.AccountNotFoundException;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Resolves the signed-in account for the current request, always from the
 * database rather than from the session snapshot.
 *
 * <p>This is what makes a disabled account, a revoked role or a new verification
 * decision take effect on the very next request rather than whenever the session
 * happens to expire. The session keeps a principal so that Spring Security can
 * route requests cheaply; it is never the authority for a sensitive decision.
 *
 * <p>Disabled accounts are cut off two ways: the request is refused, and the
 * session is invalidated so the cookie cannot be replayed. Deleting the session
 * from MongoDB is what actually stops a stolen cookie from working.
 *
 * <p>The loaded account is memoized per request, so a controller and the service
 * it calls observe the same state without repeating the lookup.
 */
@Component
public class AccountAccess {

    private static final String REQUEST_ATTRIBUTE = AccountAccess.class.getName() + ".account";

    private final UserService userService;

    public AccountAccess(UserService userService) {
        this.userService = userService;
    }

    /**
     * The signed-in account for the current request.
     *
     * @throws AccountDisabledException if the account has been disabled
     * @throws AccountNotFoundException if the session refers to an account that no longer exists
     */
    public User requireCurrentUser() {
        Optional<AuthenticatedUser> principal = currentPrincipal();
        if (principal.isEmpty()) {
            throw new AccountNotFoundException("No account is signed in for the current request.");
        }
        return requireUser(principal.get().userId());
    }

    /**
     * The signed-in account for the given principal, re-checked against current
     * stored state. Used by security rules, which have a principal in hand but
     * no controller.
     */
    public User requireUser(AuthenticatedUser principal) {
        return requireUser(principal.userId());
    }

    /** Whether an account is signed in, without touching the database. */
    public boolean isSignedIn() {
        return currentPrincipal().isPresent();
    }

    private User requireUser(org.bson.types.ObjectId userId) {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            Object memoized = request.getAttribute(REQUEST_ATTRIBUTE);
            if (memoized instanceof User cached && cached.getId().equals(userId)) {
                return requireEnabled(cached, request);
            }
        }

        User user = userService.findById(userId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "The account for this session no longer exists."));

        if (request != null) {
            request.setAttribute(REQUEST_ATTRIBUTE, user);
        }
        return requireEnabled(user, request);
    }

    private User requireEnabled(User user, HttpServletRequest request) {
        if (user.isEnabled()) {
            return user;
        }
        invalidateSession(request);
        throw new AccountDisabledException(
                "This account has been disabled. Contact an administrator to restore access.");
    }

    /**
     * Destroys the current session and clears the security context, so nothing
     * downstream sees a stale identity. On Spring Session this also deletes the
     * stored session from MongoDB, which is what actually stops a captured
     * cookie from being replayed.
     */
    public void invalidateSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request == null) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /**
     * The session principal, without the {@code OAuth2AuthenticationToken} that
     * Spring Security briefly holds during the login handshake.
     */
    public Optional<AuthenticatedUser> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication instanceof OAuth2AuthenticationToken) {
            // Mid-handshake: the application principal has not been resolved yet.
            return Optional.empty();
        }
        return authentication.getPrincipal() instanceof AuthenticatedUser principal
                ? Optional.of(principal)
                : Optional.empty();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
