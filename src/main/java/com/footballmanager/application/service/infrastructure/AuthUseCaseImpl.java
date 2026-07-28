package com.footballmanager.application.service.infrastructure;

import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.port.in.auth.AuthLoginCommand;
import com.footballmanager.domain.port.in.auth.AuthRefreshCommand;
import com.footballmanager.domain.port.in.auth.AuthRegisterCommand;
import com.footballmanager.domain.port.in.auth.AuthTokenResult;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.ports.out.user.UserRepository;
import com.footballmanager.domain.port.in.auth.AuthUserInfo;
import com.footballmanager.domain.port.in.auth.AuthUseCase;
import com.footballmanager.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Authentication use case implementation.
 *
 * Login does not initialize the world view or write to Redis; data is loaded
 * on demand when the application needs it.
 */
@Service
@RequiredArgsConstructor
public class AuthUseCaseImpl implements AuthUseCase {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Mono<AuthTokenResult> register(AuthRegisterCommand command) {
        return userRepository.findByEmail(command.email())
            .<User>flatMap(user -> Mono.error(new IllegalArgumentException("Email already exists")))
            .switchIfEmpty(Mono.defer(() -> {
                String encodedPassword = passwordEncoder.encode(command.password());
                return userRepository.createNew(command.email(), command.username(), encodedPassword);
            }))
            .flatMap(user -> generateTokenResponse(user));
    }

    @Override
    public Mono<AuthTokenResult> login(AuthLoginCommand command) {
        return userRepository.findByEmail(command.email())
            .switchIfEmpty(Mono.defer(() -> Mono.error(new IllegalArgumentException("User not found"))))
            .filterWhen(user -> Mono.fromCallable(() ->
                passwordEncoder.matches(command.password(), user.getPasswordHash())))
            .switchIfEmpty(Mono.defer(() -> Mono.error(new IllegalArgumentException("Invalid password"))))
            .flatMap(user -> {
                return generateTokenResponse(user);
            });
    }

    @Override
    public Mono<AuthTokenResult> refreshToken(AuthRefreshCommand command) {
        if (!jwtTokenProvider.validateToken(command.refreshToken())) {
            return Mono.error(new IllegalArgumentException("Invalid refresh token"));
        }

        String userId = jwtTokenProvider.getUserIdFromToken(command.refreshToken());
        String role = jwtTokenProvider.getRoleFromToken(command.refreshToken());

        String newAccessToken = jwtTokenProvider.generateToken(userId, role);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        return Mono.just(new AuthTokenResult(
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
    public Mono<AuthUserInfo> getUserInfo(String userId) {
        return userRepository.findById(UUID.fromString(userId))
            .flatMap(user -> {
                AuthUserInfo info = new AuthUserInfo(
                        user.getId().getValue().toString(),
                        user.getEmail(),
                        user.getUsername(),
                        user.getUsername(),
                        user.getTeamId() != null ? user.getTeamId().toString() : null,
                        null);

                if (user.getTeamId() != null) {
                    return teamRepository.findById(UUID.fromString(userId), user.getTeamId())
                        .map(team -> {
                            return new AuthUserInfo(
                                    info.id(), info.email(), info.username(), info.displayName(),
                                    info.teamId(), team.getName());
                        })
                        .defaultIfEmpty(info);
                }
                return Mono.just(info);
            });
    }

    private Mono<AuthTokenResult> generateTokenResponse(User user) {
        return Mono.fromCallable(() -> {
            String accessToken = jwtTokenProvider.generateToken(
                user.getId().getValue().toString(),
                user.getRole().name());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().getValue().toString());
            return new AuthTokenResult(accessToken, refreshToken,
                jwtTokenProvider.getExpirationTime(), "Bearer");
        });
    }
}
