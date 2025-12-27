package com.footballmanager.adapters.in.web;

import com.footballmanager.application.dto.LoginRequest;
import com.footballmanager.application.dto.RegisterUserRequest;
import com.footballmanager.application.dto.JwtTokenResponse;
import com.footballmanager.application.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public Mono<ResponseEntity<JwtTokenResponse>> register(@RequestBody RegisterUserRequest request) {
        return authService.register(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<JwtTokenResponse>> login(@RequestBody LoginRequest request) {
        return authService.login(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().build()));
    }
}
