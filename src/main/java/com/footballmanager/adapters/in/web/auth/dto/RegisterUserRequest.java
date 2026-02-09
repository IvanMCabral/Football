package com.footballmanager.adapters.in.web.auth.dto;

public record RegisterUserRequest(
    String email,
    String username,
    String password
) {}
