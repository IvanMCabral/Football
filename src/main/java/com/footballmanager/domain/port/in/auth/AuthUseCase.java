package com.footballmanager.domain.port.in.auth;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AuthUseCase {
    Mono<AuthUserInfo> getUserInfo(String userId);
    Mono<Void> assignTeam(String userId, UUID teamId);
    Mono<AuthTokenResult> register(AuthRegisterCommand command);
    Mono<AuthTokenResult> login(AuthLoginCommand command);
    Mono<AuthTokenResult> refreshToken(AuthRefreshCommand command);
}
