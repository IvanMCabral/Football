package com.footballmanager.adapters.in.web.auth;

import com.footballmanager.adapters.in.web.auth.dto.*;
import com.footballmanager.domain.port.in.auth.AuthUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private final AuthUseCase authUseCase;

    @GetMapping("/me")
    public Mono<ResponseEntity<UserInfoResponse>> getCurrentUser(Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return authUseCase.getUserInfo(userId)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build()));
    }

    @PostMapping("/assign-team")
    public Mono<ResponseEntity<String>> assignTeamToUser(@RequestBody AssignTeamRequest request, Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        return authUseCase.assignTeam(userId, request.teamId())
            .thenReturn(ResponseEntity.ok("Team assigned successfully"))
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body("Could not assign team: " + e.getMessage())));
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<String>> register(@RequestBody RegisterUserRequest request) {
        return authUseCase.register(request)
            .thenReturn(ResponseEntity.ok("User registered"))
            .onErrorResume(e -> {
                if (e instanceof IllegalArgumentException && e.getMessage().contains("Email already exists")) {
                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body("Email is already registered"));
                }
                return Mono.just(ResponseEntity.badRequest().body("Registration error: " + e.getMessage()));
            });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<JwtTokenResponse>> login(@RequestBody LoginRequest request) {
        return authUseCase.login(request)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> {
                return Mono.just(ResponseEntity.badRequest().build());
            });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<JwtTokenResponse>> refresh(@RequestBody RefreshTokenRequest request) {
        return authUseCase.refreshToken(request)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }
}
