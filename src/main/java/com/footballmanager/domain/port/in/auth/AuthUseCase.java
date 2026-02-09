package com.footballmanager.domain.port.in.auth;

import com.footballmanager.adapters.in.web.auth.dto.JwtTokenResponse;
import com.footballmanager.adapters.in.web.auth.dto.LoginRequest;
import com.footballmanager.adapters.in.web.auth.dto.RefreshTokenRequest;
import com.footballmanager.adapters.in.web.auth.dto.RegisterUserRequest;
import com.footballmanager.adapters.in.web.auth.dto.UserInfoResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AuthUseCase {
    Mono<UserInfoResponse> getUserInfo(String userId);
    Mono<Void> assignTeam(String userId, UUID teamId);
    Mono<JwtTokenResponse> register(RegisterUserRequest request);
    Mono<JwtTokenResponse> login(LoginRequest request);
    Mono<JwtTokenResponse> refreshToken(RefreshTokenRequest request);
}
