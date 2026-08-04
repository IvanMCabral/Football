package com.footballmanager.application.service.infrastructure;

import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.application.exception.AuthConflictException;
import com.footballmanager.application.exception.AuthCredentialsException;
import com.footballmanager.application.exception.AuthValidationException;
import com.footballmanager.domain.port.in.auth.AuthLoginCommand;
import com.footballmanager.domain.port.in.auth.AuthRefreshCommand;
import com.footballmanager.domain.port.in.auth.AuthRegisterCommand;
import com.footballmanager.domain.port.in.auth.AuthTokenResult;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.ports.out.auth.AuthTokenService;
import com.footballmanager.domain.ports.out.user.UserRepository;
import com.footballmanager.domain.port.in.auth.AuthUserInfo;
import com.footballmanager.domain.port.in.auth.AuthUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

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
    private final AuthTokenService authTokenService;

    @Override
    public Mono<AuthTokenResult> register(AuthRegisterCommand command) {
        validateRegistration(command);
        AuthRegisterCommand normalizedCommand = new AuthRegisterCommand(
            command.email().trim(), command.username().trim(), command.password());
        validatePassword(normalizedCommand.password());
        return userRepository.findByEmail(normalizedCommand.email())
            .<User>flatMap(user -> Mono.error(new AuthConflictException("Email already exists")))
            .switchIfEmpty(Mono.defer(() -> {
                return userRepository.existsByUsername(normalizedCommand.username())
                    .flatMap(usernameExists -> {
                        if (usernameExists) {
                            return Mono.error(new AuthConflictException("Username already exists"));
                        }
                        String encodedPassword = passwordEncoder.encode(normalizedCommand.password());
                        return userRepository.createNew(normalizedCommand.email(), normalizedCommand.username(), encodedPassword);
                    });
            }))
            .onErrorMap(DataIntegrityViolationException.class,
                error -> new AuthConflictException("Email or username already exists"))
            .flatMap(user -> generateTokenResponse(user));
    }

    @Override
    public Mono<AuthTokenResult> login(AuthLoginCommand command) {
        validatePasswordShape(command.password());
        return userRepository.findByEmail(command.email())
            .switchIfEmpty(Mono.defer(() -> Mono.error(new AuthCredentialsException("Invalid credentials"))))
            .filterWhen(user -> Mono.fromCallable(() ->
                passwordEncoder.matches(command.password(), user.getPasswordHash())))
            .switchIfEmpty(Mono.defer(() -> Mono.error(new AuthCredentialsException("Invalid credentials"))))
            .flatMap(user -> {
                return generateTokenResponse(user);
            });
    }

    @Override
    public Mono<AuthTokenResult> refreshToken(AuthRefreshCommand command) {
        if (!authTokenService.validateToken(command.refreshToken())) {
            return Mono.error(new IllegalArgumentException("Invalid refresh token"));
        }

        String userId = authTokenService.getUserIdFromToken(command.refreshToken());
        String role = authTokenService.getRoleFromToken(command.refreshToken());

        String newAccessToken = authTokenService.generateToken(userId, role);
        String newRefreshToken = authTokenService.generateRefreshToken(userId);

        return Mono.just(new AuthTokenResult(
            newAccessToken, newRefreshToken,
            authTokenService.getExpirationTime(), "Bearer"));
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
            String accessToken = authTokenService.generateToken(
                user.getId().getValue().toString(),
                user.getRole().name());
            String refreshToken = authTokenService.generateRefreshToken(user.getId().getValue().toString());
            return new AuthTokenResult(accessToken, refreshToken,
                authTokenService.getExpirationTime(), "Bearer");
        });
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() > 128 || password.isBlank()) {
            throw new AuthValidationException("Invalid password");
        }
        if (password.length() < 8) {
            throw new AuthValidationException("Password does not meet minimum requirements");
        }
    }

    private static void validatePasswordShape(String password) {
        if (password == null || password.length() > 128 || password.isBlank()) {
            throw new AuthCredentialsException("Invalid credentials");
        }
    }

    private static void validateRegistration(AuthRegisterCommand command) {
        if (command == null) {
            throw new AuthValidationException("Invalid registration request");
        }
        String email = command.email() == null ? "" : command.email().trim();
        if (email.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new AuthValidationException("Invalid email");
        }
        String username = command.username() == null ? "" : command.username().trim();
        if (username.length() < 3 || username.length() > 50 || !username.matches("^[A-Za-z0-9_.-]+$")) {
            throw new AuthValidationException("Invalid username");
        }
    }
}
