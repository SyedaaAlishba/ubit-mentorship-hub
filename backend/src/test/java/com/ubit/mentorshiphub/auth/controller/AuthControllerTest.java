package com.ubit.mentorshiphub.auth.controller;

import com.ubit.mentorshiphub.auth.dto.AuthState;
import com.ubit.mentorshiphub.auth.dto.CurrentUserResponse;
import com.ubit.mentorshiphub.auth.dto.OnboardingRequest;
import com.ubit.mentorshiphub.common.exception.GlobalExceptionHandler;
import com.ubit.mentorshiphub.common.security.AccountAccess;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The authentication endpoints, including the ownership rule: the applicant is
 * whatever the session resolves to, and a role is never taken from the body.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AccountAccess accountAccess;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // The real advice is registered so status mapping is exercised here too.
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(accountAccess, userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @Test
    @DisplayName("an anonymous caller gets NOT_AUTHENTICATED rather than a 401")
    void anonymousMeIsNotAuthenticated() throws Exception {
        when(accountAccess.isSignedIn()).thenReturn(false);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOT_AUTHENTICATED"))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    @DisplayName("an account with no role is told to onboard")
    void onboardingRequired() throws Exception {
        when(accountAccess.isSignedIn()).thenReturn(true);
        when(accountAccess.requireCurrentUser()).thenReturn(user(null, VerificationStatus.UNVERIFIED));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(AuthState.ONBOARDING_REQUIRED.name()))
                .andExpect(jsonPath("$.onboardingComplete").value(false));
    }

    @Test
    @DisplayName("an account with role STUDENT and status UNVERIFIED reads as VERIFICATION_REQUIRED")
    void studentUnverifiedIsVerificationRequired() throws Exception {
        when(accountAccess.isSignedIn()).thenReturn(true);
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.STUDENT, VerificationStatus.UNVERIFIED));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(AuthState.VERIFICATION_REQUIRED.name()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.onboardingComplete").value(true))
                .andExpect(jsonPath("$.canSubmitVerification").value(true));
    }

    @Test
    @DisplayName("an account with role ALUMNI and status UNVERIFIED reads as VERIFICATION_REQUIRED")
    void alumniUnverifiedIsVerificationRequired() throws Exception {
        when(accountAccess.isSignedIn()).thenReturn(true);
        when(accountAccess.requireCurrentUser()).thenReturn(user(AppRole.ALUMNI, VerificationStatus.UNVERIFIED));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(AuthState.VERIFICATION_REQUIRED.name()))
                .andExpect(jsonPath("$.role").value("ALUMNI"))
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"))
                .andExpect(jsonPath("$.onboardingComplete").value(true))
                .andExpect(jsonPath("$.canSubmitVerification").value(true));
    }

    @Test
    @DisplayName("onboarding records the claimed role and leaves it unverified")
    void onboardingRecordsClaim() throws Exception {
        User current = user(null, VerificationStatus.UNVERIFIED);
        when(accountAccess.requireCurrentUser()).thenReturn(current);
        when(userService.completeOnboarding(eq(current), eq(AppRole.STUDENT))).thenAnswer(invocation -> {
            User updated = current;
            updated.setRole(AppRole.STUDENT);
            return updated;
        });

        mockMvc.perform(post("/api/auth/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.state").value(AuthState.VERIFICATION_REQUIRED.name()));
    }

    @Test
    @DisplayName("a client cannot appoint itself an administrator")
    void refusesAdminOnboarding() throws Exception {
        User current = user(null, VerificationStatus.UNVERIFIED);
        when(accountAccess.requireCurrentUser()).thenReturn(current);

        // The service refuses SENIOR and ADMIN, so the request is a conflict
        // rather than a privilege escalation.
        org.mockito.Mockito.doThrow(new com.ubit.mentorshiphub.common.exception.OnboardingConflictException(
                "Onboarding accepts only CURRENT_STUDENT or ALUMNI."))
                .when(userService).completeOnboarding(any(), eq(AppRole.ADMIN));

        mockMvc.perform(post("/api/auth/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a client cannot declare itself verified")
    void clientCannotSetVerificationStatus() throws Exception {
        // Onboarding takes only a role, so there is no field to smuggle a status in.
        when(accountAccess.requireCurrentUser()).thenReturn(user(null, VerificationStatus.UNVERIFIED));
        when(userService.completeOnboarding(any(), any())).thenReturn(user(AppRole.STUDENT, VerificationStatus.UNVERIFIED));

        mockMvc.perform(post("/api/auth/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"verificationStatus\":\"VERIFIED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("UNVERIFIED"));
    }

    @Test
    @DisplayName("a request naming another user id is ignored")
    void ignoresClientSuppliedUserId() throws Exception {
        User current = user(null, VerificationStatus.UNVERIFIED);
        when(accountAccess.requireCurrentUser()).thenReturn(current);
        when(userService.completeOnboarding(any(), any())).thenAnswer(invocation -> {
            User updated = current;
            updated.setRole(AppRole.ALUMNI);
            return updated;
        });

        mockMvc.perform(post("/api/auth/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ALUMNI\",\"userId\":\"64b000000000000000000099\"}"))
                .andExpect(status().isOk());

        // Ownership came from the session, so the body could not redirect it.
        verify(userService).completeOnboarding(current, AppRole.ALUMNI);
    }

    @Test
    @DisplayName("a missing role is a validation failure")
    void requiresRole() throws Exception {
        // No stub on accountAccess: @Valid rejects the body before the controller
        // runs, so the account is never looked up. The assertion that matters is
        // that no role was written.
        mockMvc.perform(post("/api/auth/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());

        verify(userService, never()).completeOnboarding(any(), any());
    }

    @Test
    @DisplayName("the login url points at the Google authorization endpoint")
    void exposesLoginUrl() throws Exception {
        when(accountAccess.isSignedIn()).thenReturn(false);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginUrl").value("/oauth2/authorization/google"));
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
