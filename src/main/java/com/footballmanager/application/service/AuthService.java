package com.footballmanager.application.service;

import com.footballmanager.application.dto.LoginRequest;
import com.footballmanager.application.dto.RegisterUserRequest;
import com.footballmanager.application.dto.JwtTokenResponse;
import com.footballmanager.domain.model.User;
import com.footballmanager.domain.model.UserId;
import com.footballmanager.domain.ports.out.UserRepository;
import com.footballmanager.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public Mono<JwtTokenResponse> register(RegisterUserRequest request) {
        return userRepository.findByEmail(request.email())
                .flatMap(user -> Mono.error(new IllegalArgumentException("Email already exists")))
                .switchIfEmpty(
                    Mono.defer(() -> {
                        UserId userId = UserId.generate();
                        String encodedPassword = passwordEncoder.encode(request.password());
                        User user = User.create(userId, request.email(), request.username(),
                                              encodedPassword);
                        return userRepository.save(user)
                                .then(generateTokenResponse(user));
                    })
                )
                .cast(JwtTokenResponse.class);
    }

    public Mono<JwtTokenResponse> login(LoginRequest request) {
        return userRepository.findByEmail(request.email())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found")))
                .filterWhen(user -> Mono.fromCallable(() ->
                        passwordEncoder.matches(request.password(), user.getPasswordHash())))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid password")))
                .flatMap(this::generateTokenResponse);
    }

    private Mono<JwtTokenResponse> generateTokenResponse(User user) {
        return Mono.fromCallable(() -> {
            String accessToken = jwtTokenProvider.generateToken(user.getId().getValue().toString(),
                                                              user.getRole().name());
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().getValue().toString());
            return new JwtTokenResponse(accessToken, refreshToken,
                                       jwtTokenProvider.getExpirationTime(), "Bearer");
        });
    }
}
