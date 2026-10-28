package com.ubit.mentorshiphub.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ubit.mentorshiphub.auth.dto.CurrentUserResponse;
import com.ubit.mentorshiphub.auth.dto.OnboardingRequest;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.service.UserService;

import jakarta.validation.Valid;

/**
 * Authentication and onboarding endpoints.
 *
 * <p>Sign-out itself is Spring Security's {@code POST /logout}, which clears the
 * session, deletes it from the session store and expires the cookie in one
 * correctly-ordered operation. Re-implementing that in a controller is exactly
 * the kind of half-measure that leaves a cookie behind.
 *
 * <p>{@code GET /api/auth/me} answers {@code NOT_AUTHENTICATED} for anonymous
 * callers rather than returning 401, so the frontend can distinguish "show the
 * login button" from "show an error".
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountAccess accountAccess;
    private final UserService userService;

    public AuthController(AccountAccess accountAccess, UserService userService) {
        this.accountAccess = accountAccess;
        this.userService = userService;
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        if (!accountAccess.isSignedIn()) {
            return CurrentUserResponse.notAuthenticated();
        }
        return CurrentUserResponse.of(accountAccess.requireCurrentUser());
    }

    /**
     * Completes onboarding by recording the role the user selected for themselves.
     * The account lands in {@code PENDING}: the claim is recorded now and
     * confirmed later by an administrator.
     */
    @PostMapping("/onboarding")
    public ResponseEntity<CurrentUserResponse> completeOnboarding(@Valid @RequestBody OnboardingRequest request) {
        User current = accountAccess.requireCurrentUser();
        User updated = userService.completeOnboarding(current, request.role());
        return ResponseEntity.ok(CurrentUserResponse.of(updated));
    }
}
