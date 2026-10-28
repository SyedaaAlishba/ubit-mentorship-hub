package com.ubit.mentorshiphub.auth.config;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Authentication settings that are deliberately not Google credentials.
 *
 * @param bootstrapAdminEmail optional email to grant the first administrator
 *                            role to, or {@code null} when unset
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String bootstrapAdminEmail) {

    public AuthProperties {
        bootstrapAdminEmail = normalizeEmail(bootstrapAdminEmail);
    }

    /** Whether an initial administrator has been configured at all. */
    public boolean isBootstrapAdminConfigured() {
        return this.bootstrapAdminEmail != null;
    }

    /**
     * Whether the given Google-asserted email is the configured bootstrap
     * administrator. Compared case-insensitively, matching how emails are
     * stored.
     */
    public boolean isBootstrapAdminEmail(String email) {
        if (this.bootstrapAdminEmail == null || email == null || email.isBlank()) {
            return false;
        }
        return this.bootstrapAdminEmail.equalsIgnoreCase(email.trim());
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            // Empty means "no bootstrap administrator configured", which is the
            // default so the feature is opt-in rather than always-on.
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf('@') > 0 ? normalized : null;
    }
}
