package com.footballmanager.application.service.infrastructure;

import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.application.exception.AuthConflictException;
import com.footballmanager.application.exception.AuthCredentialsException;
import com.footballmanager.application.exception.AuthValidationException;
import com.footballmanager.application.exception.TeamAlreadyAssignedException;
import com.footballmanager.domain.port.in.auth.AuthLoginCommand;
import com.footballmanager.domain.port.in.auth.AuthRefreshCommand;
import com.footballmanager.domain.port.in.auth.AuthRegisterCommand;
import com.footballmanager.domain.port.in.auth.AuthTokenResult;
import com.footballmanager.domain.ports.out.team.TeamRepository;
import com.footballmanager.domain.ports.out.auth.AuthTokenService;
import com.footballmanager.domain.ports.out.user.UserRepository;
import com.footballmanager.domain.port.in.auth.AuthUserInfo;
import com.footballmanager.domain.port.in.auth.AuthUseCase;
import com.footballmanager.domain.service.PasswordContract;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import io.r2dbc.postgresql.api.PostgresqlException;

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
        validateRegistrationPassword(normalizedCommand.password());
        return userRepository.findByEmail(normalizedCommand.email())
            .<User>flatMap(user -> Mono.error(new AuthConflictException("Email already exists")))
            .switchIfEmpty(Mono.defer(() -> {
                return userRepository.existsByUsername(normalizedCommand.username())
                    .flatMap(usernameExists -> {
                        if (usernameExists) {
                            return Mono.error(new AuthConflictException("Username already exists"));
                        }
                        String encodedPassword = encodePassword(normalizedCommand.password());
                        return userRepository.createNew(normalizedCommand.email(), normalizedCommand.username(), encodedPassword);
                    });
            }))
            .onErrorMap(DataIntegrityViolationException.class,
                error -> new AuthConflictException("Email or username already exists"))
            .flatMap(user -> generateTokenResponse(user));
    }

    @Override
    public Mono<AuthTokenResult> login(AuthLoginCommand command) {
        if (command == null) {
            throw new AuthCredentialsException("Invalid credentials");
        }
        validatePasswordShape(command.password());
        return userRepository.findByEmail(command.email())
            .switchIfEmpty(Mono.defer(() -> Mono.error(new AuthCredentialsException("Invalid credentials"))))
            .filterWhen(user -> Mono.fromCallable(() -> matchesPassword(
                command.password(), user.getPasswordHash())))
            .switchIfEmpty(Mono.defer(() -> Mono.error(new AuthCredentialsException("Invalid credentials"))))
            .flatMap(user -> {
                return generateTokenResponse(user);
            });
    }

    @Override
    public Mono<AuthTokenResult> refreshToken(AuthRefreshCommand command) {
        return Mono.defer(() -> {
            String refreshToken = command == null ? null : command.refreshToken();
            if (refreshToken == null || !authTokenService.validateToken(refreshToken)) {
                return Mono.error(new AuthCredentialsException("Invalid refresh token"));
            }

            final UUID userId;
            try {
                userId = UUID.fromString(authTokenService.getUserIdFromToken(refreshToken));
            } catch (RuntimeException invalidSubject) {
                return Mono.error(new AuthCredentialsException("Invalid refresh token"));
            }

            return userRepository.findById(userId)
                .switchIfEmpty(Mono.defer(() ->
                    Mono.error(new AuthCredentialsException("Invalid refresh token"))))
                .map(user -> {
                    String canonicalUserId = user.getId().getValue().toString();
                    String canonicalRole = user.getRole().name();
                    String newAccessToken = authTokenService.generateToken(canonicalUserId, canonicalRole);
                    String newRefreshToken = authTokenService.generateRefreshToken(canonicalUserId);
                    return new AuthTokenResult(
                        newAccessToken, newRefreshToken,
                        authTokenService.getExpirationTime(), "Bearer");
                });
        });
    }

    @Override
    public Mono<Void> assignTeam(String userId, UUID teamId) {
        return userRepository.findById(UUID.fromString(userId))
            .switchIfEmpty(Mono.defer(() -> Mono.error(new IllegalArgumentException("User not found"))))
            .flatMap(user -> userRepository.findByTeamId(teamId)
                .filter(owner -> !owner.getId().equals(user.getId()))
                .flatMap(owner -> Mono.<Void>error(new TeamAlreadyAssignedException()))
                .switchIfEmpty(Mono.defer(() -> {
                    user.setTeamId(teamId);
                    return userRepository.save(user).then();
                })))
            .onErrorMap(AuthUseCaseImpl::isSingleOwnerConstraintViolation,
                ignored -> new TeamAlreadyAssignedException());
    }

    private static boolean isSingleOwnerConstraintViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PostgresqlException postgresqlException
                    && "23505".equals(postgresqlException.getErrorDetails().getCode())
                    && "uk_users_team_id_single_owner".equals(
                        postgresqlException.getErrorDetails().getConstraintName().orElse(null))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    private static void validateRegistrationPassword(String password) {
        if (!PasswordContract.isRegistrationPasswordValid(password)) {
            throw new AuthValidationException(
                "Password must contain at least 8 characters and no more than 72 UTF-8 bytes");
        }
    }

    private static void validatePasswordShape(String password) {
        if (!PasswordContract.isHashablePassword(password)) {
            throw new AuthCredentialsException("Invalid credentials");
        }
    }

    private String encodePassword(String password) {
        try {
            return passwordEncoder.encode(password);
        } catch (IllegalArgumentException encodingFailure) {
            throw new AuthValidationException("Invalid password");
        }
    }

    private boolean matchesPassword(String password, String passwordHash) {
        try {
            return passwordEncoder.matches(password, passwordHash);
        } catch (IllegalArgumentException matchingFailure) {
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
