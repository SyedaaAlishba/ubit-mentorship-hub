package com.ubit.mentorshiphub.auth.config;

import com.ubit.mentorshiphub.auth.security.AuthUserService;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.support.InMemorySessionTestConfiguration;
import com.ubit.mentorshiphub.user.model.AppRole;
import com.ubit.mentorshiphub.user.model.User;
import com.ubit.mentorshiphub.user.model.VerificationStatus;
import jakarta.servlet.http.Cookie;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security filter chain, exercised over real HTTP so that the rules are
 * verified as configured rather than as intended.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InMemorySessionTestConfiguration.class)
@TestPropertySource(properties = "app.auth.bootstrap-admin-email=")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private AccountAccess accountAccess;

    @BeforeEach
    void restoreProductionCsrfTokenRepository() {
        // SecurityMockMvcRequestPostProcessors.csrf() installs a session-backed
        // test repository by mutating the singleton CsrfFilter through
        // WebTestUtils.setCsrfTokenRepository (reflection on its private
        // "tokenRepository" field). That swap outlives the request, so without
        // this reset any test running after a .with(csrf()) test would silently
        // stop issuing the XSRF-TOKEN cookie and the results would depend on
        // execution order. Reinstall the repository the chain is configured
        // with so every test exercises the real behaviour.
        CsrfFilter csrfFilter = springSecurityFilterChain.getFilters("/api/auth/me").stream()
                .filter(CsrfFilter.class::isInstance)
                .map(CsrfFilter.class::cast)
                .findFirst()
                .orElseThrow();
        ReflectionTestUtils.setField(csrfFilter, "tokenRepository",
                CookieCsrfTokenRepository.withHttpOnlyFalse());
    }

    @Test
    @DisplayName("the auth probe answers anonymously instead of redirecting to a login page")
    void authProbeIsPublicAndAnswersJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value("NOT_AUTHENTICATED"));
    }

    @Test
    @DisplayName("an anonymous call to a protected API is a JSON 401, not an HTML redirect")
    void protectedApiRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/verification/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Sign in to continue."));
    }

    @Test
    @DisplayName("anonymous alumni discovery is a JSON 401, not an HTML redirect")
    void alumniDiscoveryRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/alumni"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Sign in to continue."));
    }

    @Test
    @DisplayName("anonymous access to the mentorship endpoints is a JSON 401")
    void mentorshipRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/mentorship/active"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Sign in to continue."));
        mockMvc.perform(post("/api/mentorship/requests")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\":\"64b0000000000000000000b2\","
                                + "\"message\":\"I would value your guidance on backend engineering.\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("anonymous access to the administrator verification endpoints is a JSON 401")
    void adminVerificationRejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/verification/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Sign in to continue."));
        mockMvc.perform(get("/api/admin/verification/applications/64b0000000000000000000a1/document"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/verification/applications/64b0000000000000000000a1/decide")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"VERIFIED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an anonymous POST with no CSRF token is stopped by CSRF before a controller runs")
    void anonymousWriteIsStoppedByCsrf() throws Exception {
        // 403 rather than 401: the request has no CSRF token, so the CsrfFilter
        // rejects it first. That is the correct order - an unauthenticated
        // cross-site write must never even be dispatched.
        mockMvc.perform(post("/api/verification/alumni")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passoutYear\":2024,\"linkedInUrl\":\"https://www.linkedin.com/in/aliraza\"}"))
                .andExpect(status().isForbidden());
    }
    @Test
    @DisplayName("onboarding requires a session, so an anonymous caller is a 401 even with a valid CSRF token")
    void onboardingRejectsAnonymous() throws Exception {
        // The token removes the CSRF objection, which isolates the actual rule
        // under test: onboarding is not an anonymous endpoint.
        mockMvc.perform(post("/api/auth/onboarding")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the Google authorization endpoint is reachable so open signup works")
    void googleAuthorizationEndpointIsPublic() throws Exception {
        // A 302 to accounts.google.com is what proves the redirect filter is
        // wired to the standard /oauth2/authorization/{registrationId} pattern.
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("the standard OAuth2 callback path is routed to Spring Security")
    void callbackPathIsRouted() throws Exception {
        // Reaching the login processing filter (which redirects on an invalid
        // code) proves the redirection endpoint is wired to the standard path.
        mockMvc.perform(get("/login/oauth2/code/google"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("there is no password login form to fall back to")
    void passwordLoginIsDisabled() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("health is public so a platform can probe it")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("sign-out is reachable and answers 204 for the frontend to route on")
    void logoutIsAvailable() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent())
                // The cookie is expired, not omitted: Max-Age=0 is how a browser is
                // told to drop it. Leaving the session cookie in place would let the
                // next request resume the same principal.
                .andExpect(result -> {
                    jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie("UBITSESSION");
                    assertThat(cookie).isNotNull();
                    assertThat(cookie.getMaxAge()).isZero();
                });
    }

    @Test
    @DisplayName("an anonymous sign-out is a no-op rather than an error")
    void logoutWithoutSessionSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the 401 body is the shared ApiError shape, not ad-hoc JSON")
    void unauthorizedUsesTheSharedErrorShape() throws Exception {
        mockMvc.perform(get("/api/verification/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/verification/me"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @DisplayName("no cross-origin grant is issued by default, so the same-origin proxy is the only path")
    void noCorsGrantByDefault() throws Exception {
        // A preflight gets no Access-Control-Allow-Origin, so the browser blocks
        // the real request. Allowed origins are empty unless configured.
        mockMvc.perform(options("/api/verification/me")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
                        .isNull());
    }

    @Test
    @DisplayName("nothing counts as signed in without a session")
    void nothingIsSignedInWithoutSession() {
        assertThat(accountAccess.isSignedIn()).isFalse();
    }

    @Test
    @DisplayName("an unset bootstrap address is not a configured one")
    void bootstrapIsOptIn() {
        assertThat(new AuthProperties("").isBootstrapAdminConfigured()).isFalse();
        assertThat(new AuthProperties("   ").isBootstrapAdminConfigured()).isFalse();
        assertThat(new AuthProperties(null).isBootstrapAdminConfigured()).isFalse();
    }

    @Test
    @DisplayName("a configured bootstrap address matches case-insensitively and ignores surrounding space")
    void bootstrapMatchesLoosely() {
        AuthProperties properties = new AuthProperties("  Admin@Ubit.edu.PK ");

        assertThat(properties.isBootstrapAdminConfigured()).isTrue();
        assertThat(properties.isBootstrapAdminEmail("ADMIN@ubit.edu.pk")).isTrue();
        assertThat(properties.isBootstrapAdminEmail("someone.else@gmail.com")).isFalse();
        assertThat(properties.isBootstrapAdminEmail(null)).isFalse();
    }

    @Test
    @DisplayName("a value with no @ is not accepted as a bootstrap address")
    void bootstrapRejectsNonEmail() {
        assertThat(new AuthProperties("not-an-email").isBootstrapAdminConfigured()).isFalse();
    }

    @Test
    @DisplayName("a safe request emits the readable XSRF-TOKEN cookie the SPA echoes back")
    void safeRequestEmitsReadableCsrfCookie() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    Cookie token = result.getResponse().getCookie("XSRF-TOKEN");
                    assertThat(token).isNotNull();
                    assertThat(token.getValue()).isNotBlank();
                    // Readable by JavaScript, which is the whole point: an
                    // HttpOnly cookie could never be echoed in a header.
                    assertThat(token.isHttpOnly()).isFalse();
                });
    }

    @Test
    @DisplayName("a write echoing the CSRF cookie in the header clears the CSRF gate")
    void writeWithCsrfCookieAndHeaderPassesCsrf() throws Exception {
        Cookie token = mockMvc.perform(get("/api/auth/me"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();

        mockMvc.perform(post("/api/auth/onboarding")
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                // CSRF is satisfied, so the request reaches authorization: the
                // caller is still anonymous, which is a 401 and not a 403.
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a successful sign-in saves the context and redirects to the configured frontend URL")
    void oauthSuccessRedirectsToTheConfiguredFrontend() throws Exception {
        AuthUserService authUserService = mock(AuthUserService.class);
        AccountAccess accountAccess = mock(AccountAccess.class);
        SecurityContextRepository repository = mock(SecurityContextRepository.class);

        User user = new User();
        user.setId(new ObjectId());
        user.setEmail("ali@example.com");
        user.setName("Ali Raza");
        user.setRole(AppRole.STUDENT);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setEnabled(true);
        when(authUserService.resolveSignedInUser(any())).thenReturn(user);

        AuthenticationSuccessHandler handler = new SecurityConfig()
                .applicationPrincipalSuccessHandler(authUserService, accountAccess, repository,
                        "http://localhost:5173/");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, mock(OAuth2AuthenticationToken.class));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/");
        verify(repository).saveContext(any(), eq(request), eq(response));
    }
}
