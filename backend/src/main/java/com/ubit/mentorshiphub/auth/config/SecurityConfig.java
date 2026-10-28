package com.ubit.mentorshiphub.auth.config;

import java.time.Clock;
import java.util.function.Supplier;

import com.ubit.mentorshiphub.auth.security.ApplicationAuthentication;
import com.ubit.mentorshiphub.auth.security.AuthenticatedUser;
import com.ubit.mentorshiphub.auth.security.AuthUserService;
import com.ubit.mentorshiphub.auth.security.UserRoleGrantedAuthoritiesMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubit.mentorshiphub.common.api.ApiError;
import com.ubit.mentorshiphub.common.security.AccountAccess;
import com.ubit.mentorshiphub.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Security configuration for the authentication slice.
 *
 * <p>Endpoints are {@code permitAll} only where the request carries no
 * application data: the OAuth2 initiation and callback, the auth-state probe and
 * the health check. Everything else under {@code /api} requires a session, and
 * the gates in {@code AuthorizationGates} narrow that further per resource.
 *
 * <p>CSRF protection stays enabled. A session cookie on its own would let any
 * third-party page drive authenticated writes from the user's browser, so the
 * header check is load-bearing, not ceremony.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Injected wherever a timestamp is written, so tests can fix time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Replaces the principal after Google signs the user in.
     *
     * <p>Without this the session would keep the {@code OAuth2AuthenticationToken}
     * together with Google's access and refresh tokens. Replacing it is what keeps
     * those tokens out of the server-side session.
     */
    @Bean
    AuthenticationSuccessHandler applicationPrincipalSuccessHandler(AuthUserService authUserService,
                                                                   AccountAccess accountAccess,
                                                                   SecurityContextRepository securityContextRepository,
                                                                   @Value("${app.auth.post-login-redirect-uri:/}") String postLoginRedirectUri) {
        return (request, response, authentication) -> {
            User user = authUserService.resolveSignedInUser((OAuth2AuthenticationToken) authentication);
            AuthenticatedUser principal = AuthenticatedUser.from(user);
            // Refuses a disabled account, and invalidates the session in the process.
            accountAccess.requireUser(principal);

            ApplicationAuthentication replaced = new ApplicationAuthentication(
                    principal, UserRoleGrantedAuthoritiesMapper.toAuthorities(principal));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(replaced);
            securityContextRepository.saveContext(context, request, response);

            // The OAuth2 handshake is a full-page browser navigation, so the
            // response has to send the browser somewhere. Hand it back to the
            // SPA (configurable, defaulting to the same origin) which re-reads
            // /api/auth/me and routes from there. Without this the browser would
            // sit on the callback URL with an already-authenticated session.
            response.sendRedirect(postLoginRedirectUri);
        };
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        // Explicit bean so the success handler writes to the same repository the
        // filter chain reads from.
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    AuthenticationSuccessHandler applicationPrincipalSuccessHandler,
                                    ObjectMapper objectMapper) throws Exception {
        return http
                .authorizeHttpRequests(registry -> registry
                        // Google OAuth2 initiation and the standard callback path.
                        .requestMatchers("/oauth2/**", "/login/oauth2/code/**").permitAll()
                        // Only the two endpoints that are genuinely anonymous: the
                        // state probe, which reports NOT_AUTHENTICATED to itself, and
                        // sign-out, which must work in order to clear a session.
                        // Onboarding is NOT here - it is a state-changing call and
                        // requires a session, so it falls through to /api/** below.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        // Browser clients cannot read an HttpOnly session cookie, so
                        // the default session-backed token is unusable from the SPA.
                        // A readable XSRF-TOKEN cookie echoed in the X-XSRF-TOKEN
                        // header is the contract frontend/src/services/api.ts already
                        // implements. The eager handler is required: Spring Security's
                        // default handler only materializes the token when something
                        // reads it, so on a safe request no cookie would be written and
                        // the SPA's first write would have nothing to echo.
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new EagerCookieCsrfTokenRequestHandler())
                        // The OAuth2 flow is a top-level browser redirect, not an
                        // attacker-reachable form post, so CSRF does not apply to it.
                        .ignoringRequestMatchers("/oauth2/**", "/login/**"))
                // Google is the only way in: no password form, no HTTP Basic.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // Sign-out lives in the filter chain rather than a controller: the
                // LogoutFilter also invalidates the session and clears the
                // authentication from the security context, which a controller
                // returning 204 would not do. The default redirect to a login page
                // is replaced with 204 so the client can route on it.
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("UBITSESSION")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .requestCache(cache -> cache.disable())
                .oauth2Login(oauth -> oauth
                        // Both endpoints keep Spring Security's standard URIs. The
                        // authorization endpoint in particular must retain its
                        // {registrationId} placeholder, or the redirect filter
                        // stops matching and no one can sign in at all.
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/login/oauth2/code/*"))
                        .userInfoEndpoint(info -> info
                                .oidcUserService(new org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService()))
                        .successHandler(applicationPrincipalSuccessHandler))
                .sessionManagement(session -> session
                        // A new session id after login, so a pre-login id is worthless.
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(handling -> handling
                        // Anonymous access to an API call gets JSON rather than a
                        // redirect, so the frontend can react instead of following
                        // the browser to an HTML login page. The body is the shared
                        // ApiError shape, so a client parses one error format.
                        .authenticationEntryPoint(unauthorizedHandler(objectMapper)))
                .build();
    }

    /**
     * Renders {@link ApiError} for an unauthenticated API call. Hand-rolled JSON
     * would drift from the rest of the API, so the shared shape is serialised
     * through the same mapper the controllers use.
     */
    private static org.springframework.security.web.AuthenticationEntryPoint unauthorizedHandler(ObjectMapper mapper) {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), ApiError.of(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Unauthorized",
                    "Sign in to continue.",
                    request.getRequestURI(),
                    java.util.List.of()));
        };
    }

    /**
     * A {@link CsrfTokenRequestAttributeHandler} that materialises the token on
     * every request instead of leaving it deferred.
     *
     * <p>Spring Security 6 defers token creation: the configured
     * {@link CookieCsrfTokenRepository} only writes the readable {@code XSRF-TOKEN}
     * cookie once something asks for the token. Nothing on a safe GET like
     * {@code /api/auth/me} does, so the SPA would load with no cookie and its
     * first write would be rejected. Resolving the token here makes the
     * repository write the cookie on that first safe request. Once the cookie
     * exists it is loaded rather than regenerated, so this does not rewrite it
     * on every call.
     *
     * <p>Header/parameter resolution is inherited unchanged, which is what keeps
     * the echoed {@code X-XSRF-TOKEN} value identical to the cookie value.
     */
    private static final class EagerCookieCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           Supplier<CsrfToken> deferredCsrfToken) {
            // Force materialisation; the repository persists the cookie as a
            // side effect when the token is absent.
            deferredCsrfToken.get();
            super.handle(request, response, deferredCsrfToken);
        }
    }
}
