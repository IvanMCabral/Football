package com.footballmanager.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import java.util.Arrays;

import reactor.core.publisher.Mono;

/**
 * Configuración CORS para WebFlux
 */
@Configuration
public class CorsConfig {

    @Bean
    public WebFilter corsWebFilter() {
        return (ServerWebExchange exchange, WebFilterChain chain) -> {
            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();
            String origin = exchange.getRequest().getHeaders().getFirst("Origin");

            // Permitir CORS para todos los endpoints de API
            if (path.startsWith("/api")) {
                exchange.getResponse().getHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponse().getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                exchange.getResponse().getHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin, X-Requested-With");
                exchange.getResponse().getHeaders().add("Access-Control-Max-Age", "3600");
                exchange.getResponse().getHeaders().add("Access-Control-Expose-Headers", "Content-Type");

                // Handle OPTIONS preflight
                if ("OPTIONS".equals(method)) {
                    exchange.getResponse().getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                    exchange.getResponse().getHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, Origin, X-Requested-With");
                    return exchange.getResponse().setComplete();
                }
            }

            return chain.filter(exchange);
        };
    }

    @Bean
    public CorsWebFilter corsWebFilterBean() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(Arrays.asList("*"));
        corsConfig.setMaxAge(3600L);
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        corsConfig.setAllowedHeaders(Arrays.asList("*"));
        corsConfig.setExposedHeaders(Arrays.asList("Content-Type"));
        corsConfig.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
