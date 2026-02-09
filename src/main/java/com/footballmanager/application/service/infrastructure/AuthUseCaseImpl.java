package com.footballmanager.application.service.infrastructure;

import com.footballmanager.adapters.in.web.auth.dto.*;
import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.ports.out.user.UserRepository;
import com.footballmanager.domain.port.in.auth.AuthUseCase;
import com.footballmanager.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implementación de UseCase para autenticación.
 *
 * Login NO inicializa WorldView ni escribe en Redis.
 * Los datos se cargan on-demand cuando se necesitan.
 */
@Service
@RequiredArgsConstructor
public class AuthUseCaseImpl implements AuthUseCase {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<JwtTokenResponse> register(RegisterUserRequest request) {
        return userRepository.findByEmail(request.email())
            .<User>flatMap(user -> Mono.error(new IllegalArgumentException("Email already exists")))
            .switchIfEmpty(Mono.defer(() -> {
                String encodedPassword = passwordEncoder.encode(request.password());
                return userRepository.createNew(request.email(), request.username(), encodedPassword);
            }))
            .flatMap(user -> generateTokenResponse(user));
    }

    @Override
    public Mono<JwtTokenResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.email())
            .switchIfEmpty(Mono.defer(() -> Mono.error(new IllegalArgumentException("User not found"))))
            .filterWhen(user -> Mono.fromCallable(() ->
                passwordEncoder.matches(request.password(), user.getPasswordHash())))
            .switchIfEmpty(Mono.defer(() -> Mono.error(new IllegalArgumentException("Invalid password"))))
            .flatMap(user -> {
                return generateTokenResponse(user);
            });
    }

    @Override
    public Mono<JwtTokenResponse> refreshToken(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            return Mono.error(new IllegalArgumentException("Invalid refresh token"));
        }

        String userId = jwtTokenProvider.getUserIdFromToken(request.refreshToken());
        String role = jwtTokenProvider.getRoleFromToken(request.refreshToken());

        String newAccessToken = jwtTokenProvider.generateToken(userId, role);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        return Mono.just(new JwtTokenResponse(
            newAccessToken, newRefreshToken,
            jwtTokenProvider.getExpirationTime(), "Bearer"));
    }

    @Override
    public Mono<Void> assignTeam(String userId, UUID teamId) {
        return userRepository.findById(UUID.fromString(userId))
            .switchIfEmpty(Mono.defer(() -> Mono.error(new IllegalArgumentException("User not found"))))
            .flatMap(user -> {
                user.setTeamId(teamId);
                return userRepository.save(user).then();
            });
    }

    @Override
    public Mono<UserInfoResponse> getUserInfo(String userId) {
        return userRepository.findById(UUID.fromString(userId))
            .flatMap(user -> {
                UserInfoResponse info = new UserInfoResponse();
                info.id = user.getId().getValue().toString();
                info.email = user.getEmail();
                info.username = user.getUsername();
                info.teamId = user.getTeamId() != null ? user.getTeamId().toString() : null;
                info.teamName = null;

                if (user.getTeamId() != null) {
                    return teamRepository.findById(UUID.fromString(userId), user.getTeamId())
                        .map(team -> {
                            info.teamName = team.getName();
                            return info;
                        })
                        .defaultIfEmpty(info);
                }
                return Mono.just(info);
            });
    }

    private Mono<JwtTokenResponse> generateTokenResponse(User user) {
        return Mono.fromCallable(() -> {
            String accessToken = jwtTokenProvider.generateToken(
                user.getId().getValue().toString(),
                user.getRole().name());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().getValue().toString());
            return new JwtTokenResponse(accessToken, refreshToken,
                jwtTokenProvider.getExpirationTime(), "Bearer");
        });
    }
}
