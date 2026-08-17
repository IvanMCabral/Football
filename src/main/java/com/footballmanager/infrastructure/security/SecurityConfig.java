package com.footballmanager.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.adapters.in.web.common.ErrorResponseBody;
import com.footballmanager.infrastructure.config.CorsConfig;
import com.footballmanager.domain.ports.out.user.UserRepository;
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
    private final ObjectMapper objectMapper;
    private final CorsConfig corsConfig;
    private final UserRepository userRepository;

    private void addCorsHeaders(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String origin = request.getHeaders().getOrigin();

        if (corsConfig.isAllowedOrigin(origin)) {
            response.getHeaders().set("Access-Control-Allow-Origin", origin);
            response.getHeaders().set("Vary", "Origin");
            response.getHeaders().set("Access-Control-Allow-Credentials", "true");
            response.getHeaders().set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.getHeaders().set("Access-Control-Allow-Headers",
                String.join(",", corsConfig.allowedHeaders()));
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
                return chain.filter(exchange);
            }, SecurityWebFiltersOrder.FIRST)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((exchange, ex) -> {
                    addCorsHeaders(exchange);
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
                    exchange.getResponse().getHeaders().setContentType(
                        org.springframework.http.MediaType.APPLICATION_JSON);
                    String requestId = exchange.getResponse().getHeaders()
                        .getFirst(RequestCorrelationWebFilter.REQUEST_ID_HEADER);
                    ErrorResponseBody body = ErrorResponseBody.unauthorized(
                        "No autenticado.",
                        requestId == null || requestId.isBlank() ? "unavailable" : requestId);
                    String json;
                    try {
                        json = objectMapper.writeValueAsString(body);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        // Should not happen with a simple record, but defensive
                        // guard in case Jackson config is changed in the future.
                        throw new RuntimeException("Failed to serialize 401 ErrorResponseBody", e);
                    }
                    return exchange.getResponse().writeWith(
                        reactor.core.publisher.Mono.just(exchange.getResponse()
                            .bufferFactory()
                            .wrap(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    ).then();
                })
                .accessDeniedHandler((exchange, ex) -> {
                    addCorsHeaders(exchange);
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                })
            )
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .pathMatchers("/api/v1/auth/**").permitAll()
                .pathMatchers("/api/v1/health", "/api/v1/health/**").permitAll()
                .pathMatchers("/actuator/health").permitAll()
                .pathMatchers("/api/v1/players", "/api/v1/players/**").authenticated()
                .pathMatchers("/api/v1/matches", "/api/v1/matches/**").authenticated()
                .pathMatchers("/api/v1/teams", "/api/v1/teams/**").authenticated()
                .pathMatchers("/api/v1/career", "/api/v1/career/**").authenticated()
                .pathMatchers("/api/v1/world", "/api/v1/world/**").authenticated()
                .pathMatchers("/api/v1/admin/world", "/api/v1/admin/world/**").authenticated()
                .pathMatchers("/api/v1/leagues", "/api/v1/leagues/**").authenticated()
                .pathMatchers("/api/v1/match-engine", "/api/v1/match-engine/**").authenticated()
                .pathMatchers("/api/v1/fixtures", "/api/v1/fixtures/**").authenticated()
                .pathMatchers("/api/v1/games", "/api/v1/games/**").authenticated()
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
            ServerWebExchange exchange = webFilterExchange.getExchange();
            addCorsHeaders(exchange);
            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
            exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            String requestId = exchange.getRequest().getHeaders()
                .getFirst(RequestCorrelationWebFilter.REQUEST_ID_HEADER);
            ErrorResponseBody body = ErrorResponseBody.unauthorized(
                "No autenticado.", requestId == null || requestId.isBlank() ? "unavailable" : requestId);
            try {
                String json = objectMapper.writeValueAsString(body);
                return exchange.getResponse().writeWith(reactor.core.publisher.Mono.just(
                    exchange.getResponse().bufferFactory()
                        .wrap(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).then();
            } catch (com.fasterxml.jackson.core.JsonProcessingException serializationFailure) {
                return exchange.getResponse().setComplete();
            }
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
        return new CanonicalUserAuthenticationManager(userRepository);
    }

}
