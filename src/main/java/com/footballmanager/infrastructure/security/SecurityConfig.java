package com.footballmanager.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;

    public static void addCorsHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String origin = request.getHeaders().getOrigin();

        if (origin != null) {
            response.getHeaders().set("Access-Control-Allow-Origin", origin);
            response.getHeaders().set("Vary", "Origin");
            response.getHeaders().set("Access-Control-Allow-Credentials", "true");
            response.getHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.getHeaders().set("Access-Control-Allow-Headers", "Content-Type,Authorization");
        }
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .cors(cors -> cors.disable())
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .addFilterAt((exchange, chain) -> {
                String path = exchange.getRequest().getPath().toString();
                String method = exchange.getRequest().getMethod().name();
                String origin = exchange.getRequest().getHeaders().getOrigin();
                return chain.filter(exchange);
            }, SecurityWebFiltersOrder.FIRST)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((exchange, ex) -> {
                    String path = exchange.getRequest().getPath().toString();
                    String method = exchange.getRequest().getMethod().name();
                    addCorsHeaders(exchange);
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                })
                .accessDeniedHandler((exchange, ex) -> {
                    String path = exchange.getRequest().getPath().toString();
                    String method = exchange.getRequest().getMethod().name();
                    addCorsHeaders(exchange);
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                })
            )
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/api/v1/auth/**").permitAll()
                .pathMatchers("/api/v1/health").permitAll()
                .pathMatchers("/actuator/health").permitAll()
                .pathMatchers("/api/v1/players", "/api/v1/players/**").permitAll()
                .pathMatchers("/api/v1/matches", "/api/v1/matches/**").permitAll()
                .pathMatchers("/api/v1/teams", "/api/v1/teams/**").permitAll()
                .pathMatchers("/api/v1/career", "/api/v1/career/**").permitAll()
                .pathMatchers("/api/v1/world", "/api/v1/world/**").permitAll()
                .pathMatchers("/api/v1/leagues", "/api/v1/leagues/**").permitAll()
                .pathMatchers("/api/v1/match-engine", "/api/v1/match-engine/**").permitAll()
                .pathMatchers("/api/v1/fixtures", "/api/v1/fixtures/**").permitAll()
                .pathMatchers("/api/v1/games", "/api/v1/games/**").permitAll()
                .pathMatchers("/api/v1/dashboard/**").authenticated()
                .anyExchange().authenticated()
            )
            .addFilterAt(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }

    @Bean
    public AuthenticationWebFilter jwtAuthenticationFilter() {
        AuthenticationWebFilter authenticationFilter = new AuthenticationWebFilter(reactiveAuthenticationManager());
        authenticationFilter.setServerAuthenticationConverter(serverAuthenticationConverter());
        authenticationFilter.setAuthenticationFailureHandler((webFilterExchange, exception) -> {
            String path = webFilterExchange.getExchange().getRequest().getPath().toString();
            return webFilterExchange.getExchange().getResponse().setComplete();
        });
        return authenticationFilter;
    }

    @Bean
    public ServerAuthenticationConverter serverAuthenticationConverter() {
        return exchange -> {
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return Mono.empty();
            }

            String token = authHeader.substring(7);
            if (!jwtTokenProvider.validateToken(token)) {
                return Mono.empty();
            }

            String userId = jwtTokenProvider.getUserIdFromToken(token);
            String role = jwtTokenProvider.getRoleFromToken(token);

            return Mono.just(new UsernamePasswordAuthenticationToken(userId, null,
                    java.util.Collections.singletonList(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)
                    )));
        };
    }

    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager() {
        return authentication -> Mono.just(authentication);
    }

}
