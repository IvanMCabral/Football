package com.footballmanager.adapters.in.web.auth;

import com.footballmanager.adapters.in.web.auth.dto.*;
import com.footballmanager.domain.port.in.auth.AuthLoginCommand;
import com.footballmanager.domain.port.in.auth.AuthRefreshCommand;
import com.footballmanager.domain.port.in.auth.AuthRegisterCommand;
import com.footballmanager.domain.port.in.auth.AuthTokenResult;
import com.footballmanager.domain.port.in.auth.AuthUserInfo;
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
public class AuthController {

    private final AuthUseCase authUseCase;

    @GetMapping("/me")
    public Mono<ResponseEntity<UserInfoResponse>> getCurrentUser(Authentication authentication) {
        String userId = authentication != null ? authentication.getName() : null;
        if (userId == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }
        return authUseCase.getUserInfo(userId)
            .map(AuthController::toUserInfoResponse)
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
    public Mono<ResponseEntity<JwtTokenResponse>> register(@RequestBody RegisterUserRequest request) {
        return authUseCase.register(new AuthRegisterCommand(
                request.email(), request.username(), request.password()))
            .map(AuthController::toJwtTokenResponse)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> {
                if (e instanceof IllegalArgumentException && e.getMessage().contains("Email already exists")) {
                    return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                }
                return Mono.just(ResponseEntity.badRequest().build());
            });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<JwtTokenResponse>> login(@RequestBody LoginRequest request) {
        return authUseCase.login(new AuthLoginCommand(request.email(), request.password()))
            .map(AuthController::toJwtTokenResponse)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> {
                return Mono.just(ResponseEntity.badRequest().build());
            });
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<JwtTokenResponse>> refresh(@RequestBody RefreshTokenRequest request) {
        return authUseCase.refreshToken(new AuthRefreshCommand(request.refreshToken()))
            .map(AuthController::toJwtTokenResponse)
            .map(ResponseEntity::ok)
            .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    private static JwtTokenResponse toJwtTokenResponse(AuthTokenResult result) {
        return new JwtTokenResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.tokenType());
    }

    private static UserInfoResponse toUserInfoResponse(AuthUserInfo info) {
        UserInfoResponse response = new UserInfoResponse();
        response.id = info.id();
        response.email = info.email();
        response.username = info.username();
        response.displayName = info.displayName();
        response.teamId = info.teamId();
        response.teamName = info.teamName();
        return response;
    }
}
