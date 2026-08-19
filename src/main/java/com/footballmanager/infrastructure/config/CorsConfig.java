package com.footballmanager.infrastructure.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    public static final String DIAGNOSTIC_ORIGIN = "https://manager-4f952-diagnostic.web.app";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final List<String> ALLOWED_HEADERS = List.of(
        HttpHeaders.AUTHORIZATION,
        HttpHeaders.CONTENT_TYPE,
        HttpHeaders.ACCEPT,
        HttpHeaders.ORIGIN,
        "X-Requested-With",
        "X-Request-Id");

    private final List<String> allowedOrigins;

    @org.springframework.beans.factory.annotation.Autowired
    public CorsConfig(
            @Value("${app.security.cors.allowed-origins:}") String allowedOrigins,
            @Value("${app.security.cors.diagnostic-origin:" + DIAGNOSTIC_ORIGIN + "}") String diagnosticOrigin) {
        this.allowedOrigins = parseOrigins(allowedOrigins + "," + diagnosticOrigin);
    }

    public CorsConfig(String allowedOrigins) {
        this(allowedOrigins, "");
    }

    public boolean isAllowedOrigin(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }

    /** Same allowlist used by security error responses (401/403). */
    public List<String> allowedHeaders() {
        return ALLOWED_HEADERS;
    }

    public List<String> exposedHeaders() {
        return List.of(HttpHeaders.CONTENT_TYPE, REQUEST_ID_HEADER);
    }

    @Bean
    public CorsWebFilter corsWebFilterBean() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(allowedOrigins);
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        corsConfig.setAllowedHeaders(ALLOWED_HEADERS);
        corsConfig.setExposedHeaders(exposedHeaders());
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfig);

        return new CorsWebFilter(source);
    }

    private static List<String> parseOrigins(String rawOrigins) {
        if (rawOrigins == null || rawOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawOrigins.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isBlank())
            .distinct()
            .toList();
    }
}
