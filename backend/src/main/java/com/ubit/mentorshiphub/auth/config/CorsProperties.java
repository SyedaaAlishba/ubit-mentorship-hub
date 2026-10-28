package com.ubit.mentorshiphub.auth.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin configuration for the development split-port setup.
 *
 * <p>Empty by default: the Vite dev server proxies {@code /api} to this backend,
 * so the browser only ever sees one origin and no CORS grant is needed. Setting
 * allowed origins is for deployments that really do serve the frontend and the
 * API from different origins.
 *
 * <p>Allowing specific origins rather than any origin matters here because the
 * session cookie is the credential: a wildcard with credentials is rejected by
 * browsers, and a blanket allowance would hand every site on the internet a way
 * to ride the user's session.
 *
 * @param allowedOrigins comma-separated origins allowed to send credentialed requests
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).distinct().toList();
    }

    public boolean isEmpty() {
        return this.allowedOrigins.isEmpty();
    }

    @Configuration
    static class CorsConfiguration {

        @Bean
        WebMvcConfigurer corsConfigurer(CorsProperties properties) {
            return new WebMvcConfigurer() {
                @Override
                public void addCorsMappings(CorsRegistry registry) {
                    if (properties.isEmpty()) {
                        return;
                    }
                    registry.addMapping("/api/**")
                            .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                            .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                            .allowedHeaders("Content-Type", "X-XSRF-TOKEN", "X-CSRF-TOKEN")
                            .allowCredentials(true)
                            .maxAge(3600);
                }
            };
        }
    }
}
