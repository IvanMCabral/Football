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
                    // V24D12.1.1: add WWW-Authenticate: Bearer header (RFC 7235)
                    exchange.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
                    // V24D12.1: write a consistent JSON body for 401, matching the
                    // GlobalExceptionHandler.handleUnauthorized() contract. Without
                    // this, the Spring default entry point returns 401 with body
                    // empty (just WWW-Authenticate: Bearer), which is inconsistent
                    // with the JSON that the controller helper path produces. We
                    // re-use the same body shape and code value so the client can
                    // parse 401s uniformly regardless of where the rejection
                    // originated (security filter vs UnauthorizedException handler).
                    exchange.getResponse().getHeaders().setContentType(
                        org.springframework.http.MediaType.APPLICATION_JSON);
                    String json = "{\"code\":\"UNAUTHORIZED\",\"message\":\"Unauthorized: no user id in authentication\",\"status\":401}";
                    return exchange.getResponse().writeWith(
                        reactor.core.publisher.Mono.just(exchange.getResponse()
                            .bufferFactory()
                            .wrap(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    ).then();
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
                .pathMatchers("/api/v1/players", "/api/v1/players/**").authenticated()
                .pathMatchers("/api/v1/matches", "/api/v1/matches/**").authenticated()
                // V24D12-C.2: /api/v1/teams and /api/v1/fixtures are dead
                // paths (no controller backs them in the codebase). We keep
                // them as permitAll() intentionally: Spring Security WebFlux
                // cannot distinguish "public path with no resource" (404)
                // from "protected path with no auth" (401) without an
                // explicit permitAll() entry. If we removed these, the
                // security filter would cut with 401 BEFORE the routing
                // ran, and clients would get 401 instead of the
                // contractually correct 404 "path does not exist".
                // Trade-off accepted: 2 historical permitAll() entries as
                // implicit documentation of dead paths. See
                // V24D12-C.2 prompt for full analysis of the
                // 3 alternative options we considered and rejected.
                .pathMatchers("/api/v1/teams", "/api/v1/teams/**").permitAll()
                .pathMatchers("/api/v1/career", "/api/v1/career/**").authenticated()
                .pathMatchers("/api/v1/world", "/api/v1/world/**").permitAll()
                .pathMatchers("/api/v1/leagues", "/api/v1/leagues/**").permitAll()
                .pathMatchers("/api/v1/match-engine", "/api/v1/match-engine/**").permitAll()
                .pathMatchers("/api/v1/fixtures", "/api/v1/fixtures/**").permitAll()
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
